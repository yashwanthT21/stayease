package com.stayease.booking.service;

import com.stayease.booking.dto.GuestProfileResponse;
import com.stayease.booking.dto.ReservationRequest;
import com.stayease.booking.dto.ReservationResponse;
import com.stayease.booking.entity.Reservation;
import com.stayease.booking.enums.BookingSource;
import com.stayease.booking.enums.GuestStatus;
import com.stayease.booking.enums.ReservationStatus;
import com.stayease.booking.enums.VerificationStatus;
import com.stayease.booking.repository.ReservationRepository;
import com.stayease.common.client.NotificationClient;
import com.stayease.common.client.PropertyClient;
import com.stayease.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the booking module's reservation service — the busiest piece of
 * business logic in the project.
 *
 * Four things are worth pinning down here:
 *   1. the server computes nights and totalAmount (the client can't fake a price);
 *   2. the date rules (check-out after check-in, and no booking a night that has
 *      already passed);
 *   3. approve/reject only work on a PENDING request, and approval holds the dates
 *      in property-service before confirming;
 *   4. the notification hand-off between guest and manager actually happens.
 *
 * property-service and notification-service are Mockito mocks, so these tests
 * never make a real HTTP call.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    private static final Long PROPERTY_ID = 3L;
    private static final Long GUEST_ID = 42L;
    private static final Long GUEST_USER_ID = 100L;
    private static final Long MANAGER_USER_ID = 7L;
    private static final Long OWNER_USER_ID = 5L;

    @Mock
    private ReservationRepository repository;

    @Mock
    private PropertyClient propertyClient;

    @Mock
    private GuestProfileService guestProfileService;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private ReservationServiceImpl service;

    // ---------------- fixtures ----------------

    private ReservationRequest request(LocalDate checkIn, LocalDate checkOut) {
        return new ReservationRequest(
                PROPERTY_ID, GUEST_ID, checkIn, checkOut, 2,
                new BigDecimal("9000.00"), new BigDecimal("500.00"), new BigDecimal("250.00"),
                BookingSource.PLATFORM, null);
    }

    /** A three-night stay starting tomorrow — the normal, valid case. */
    private ReservationRequest validRequest() {
        return request(LocalDate.now().plusDays(1), LocalDate.now().plusDays(4));
    }

    private Reservation pendingReservation() {
        Reservation r = new Reservation();
        r.setId(11L);
        r.setPropertyId(PROPERTY_ID);
        r.setGuestId(GUEST_ID);
        r.setCheckInDate(LocalDate.now().plusDays(1));
        r.setCheckOutDate(LocalDate.now().plusDays(4));
        r.setNights(3);
        r.setGuestCount(2);
        r.setBaseAmount(new BigDecimal("9000.00"));
        r.setTotalAmount(new BigDecimal("9750.00"));
        r.setBookingSource(BookingSource.PLATFORM);
        r.setStatus(ReservationStatus.PENDING);
        return r;
    }

    private PropertyClient.PropertySummary property(Long ownerId, Long managerId) {
        return new PropertyClient.PropertySummary(
                PROPERTY_ID, ownerId, managerId, "Sea Breeze Villa", "Kochi", "14:00:00", "11:00:00");
    }

    private GuestProfileResponse guest() {
        return new GuestProfileResponse(GUEST_ID, GUEST_USER_ID, "Asha Nair", "asha@example.com",
                null, null, VerificationStatus.ID_VERIFIED, null, 1, GuestStatus.ACTIVE);
    }

    private void stubReferencesExist() {
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(true);
        when(guestProfileService.existsById(GUEST_ID)).thenReturn(true);
    }

    // ---------------- create: the server owns the maths ----------------

    @Test
    @DisplayName("create: server computes nights and totalAmount, and defaults status to PENDING")
    void createComputesNightsAndTotal() {
        stubReferencesExist();
        when(repository.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(OWNER_USER_ID, MANAGER_USER_ID)));
        when(guestProfileService.getById(GUEST_ID)).thenReturn(guest());

        ReservationResponse created = service.create(validRequest());

        assertThat(created.nights()).isEqualTo(3);
        // 9000 base + 500 cleaning + 250 service
        assertThat(created.totalAmount()).isEqualByComparingTo("9750.00");
        assertThat(created.status()).isEqualTo(ReservationStatus.PENDING);
    }

    // ---------------- create: the date rules ----------------

    @Test
    @DisplayName("create: check-out must be after check-in")
    void createRejectsCheckOutOnOrBeforeCheckIn() {
        stubReferencesExist();
        LocalDate sameDay = LocalDate.now().plusDays(2);

        assertThatThrownBy(() -> service.create(request(sameDay, sameDay)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkOutDate must be after checkInDate");

        verify(repository, never()).save(any(Reservation.class));
    }

    @Test
    @DisplayName("create: a stay cannot start in the past")
    void createRejectsPastCheckIn() {
        stubReferencesExist();

        assertThatThrownBy(() -> service.create(request(LocalDate.now().minusDays(3), LocalDate.now().plusDays(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("in the past");

        verify(repository, never()).save(any(Reservation.class));
    }

    @Test
    @DisplayName("create: a stay starting today is still allowed")
    void createAllowsCheckInToday() {
        stubReferencesExist();
        when(repository.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(OWNER_USER_ID, MANAGER_USER_ID)));
        when(guestProfileService.getById(GUEST_ID)).thenReturn(guest());

        ReservationResponse created = service.create(request(LocalDate.now(), LocalDate.now().plusDays(2)));

        assertThat(created.nights()).isEqualTo(2);
    }

    @Test
    @DisplayName("create: refuses a reservation for a property that doesn't exist")
    void createRequiresARealProperty() {
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(validRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Property not found");

        verify(repository, never()).save(any(Reservation.class));
    }

    // ---------------- create: notifying the manager ----------------

    @Test
    @DisplayName("create: the property's manager is notified that there is a request to approve")
    void createNotifiesTheManager() {
        stubReferencesExist();
        when(repository.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(OWNER_USER_ID, MANAGER_USER_ID)));
        when(guestProfileService.getById(GUEST_ID)).thenReturn(guest());

        service.create(validRequest());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationClient).notifyUser(eq(MANAGER_USER_ID), message.capture(), eq("BOOKING"));
        assertThat(message.getValue())
                .contains("New booking request")
                .contains("Sea Breeze Villa")
                .contains("Asha Nair");
    }

    @Test
    @DisplayName("create: with no manager assigned, the owner is notified instead")
    void createFallsBackToTheOwner() {
        stubReferencesExist();
        when(repository.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(OWNER_USER_ID, null)));
        when(guestProfileService.getById(GUEST_ID)).thenReturn(guest());

        service.create(validRequest());

        verify(notificationClient).notifyUser(eq(OWNER_USER_ID), anyString(), eq("BOOKING"));
    }

    // ---------------- approve / reject ----------------

    @Test
    @DisplayName("approve: holds the dates in property-service, confirms, and tells the guest")
    void approveHoldsDatesConfirmsAndNotifiesGuest() {
        Reservation reservation = pendingReservation();
        when(repository.findById(11L)).thenReturn(Optional.of(reservation));
        when(repository.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));
        when(guestProfileService.getById(GUEST_ID)).thenReturn(guest());
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(OWNER_USER_ID, MANAGER_USER_ID)));

        ReservationResponse approved = service.approve(11L);

        assertThat(approved.status()).isEqualTo(ReservationStatus.CONFIRMED);
        // The nights are only held on approval — that's the whole point of the flow.
        verify(propertyClient).markRangeBooked(
                PROPERTY_ID, reservation.getCheckInDate(), reservation.getCheckOutDate());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationClient).notifyUser(eq(GUEST_USER_ID), message.capture(), eq("BOOKING"));
        assertThat(message.getValue())
                .contains("Booking confirmed")
                .contains("Sea Breeze Villa")
                .contains("3 nights");
    }

    @Test
    @DisplayName("approve: only a PENDING request can be approved")
    void approveRejectsNonPending() {
        Reservation alreadyConfirmed = pendingReservation();
        alreadyConfirmed.setStatus(ReservationStatus.CONFIRMED);
        when(repository.findById(11L)).thenReturn(Optional.of(alreadyConfirmed));

        assertThatThrownBy(() -> service.approve(11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only a pending reservation can be approved");

        // Nothing was held and nobody was told.
        verify(propertyClient, never()).markRangeBooked(anyLong(), any(), any());
        verifyNoInteractions(notificationClient);
    }

    @Test
    @DisplayName("reject: cancels the request and tells the guest, without holding any dates")
    void rejectCancelsAndNotifiesGuest() {
        when(repository.findById(11L)).thenReturn(Optional.of(pendingReservation()));
        when(repository.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));
        when(guestProfileService.getById(GUEST_ID)).thenReturn(guest());
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(OWNER_USER_ID, MANAGER_USER_ID)));

        ReservationResponse rejected = service.reject(11L);

        assertThat(rejected.status()).isEqualTo(ReservationStatus.CANCELLED);
        verify(propertyClient, never()).markRangeBooked(anyLong(), any(), any());

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationClient).notifyUser(eq(GUEST_USER_ID), message.capture(), eq("BOOKING"));
        assertThat(message.getValue()).contains("declined");
    }

    @Test
    @DisplayName("getById: unknown id gives a not-found error")
    void getByIdThrowsWhenMissing() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
