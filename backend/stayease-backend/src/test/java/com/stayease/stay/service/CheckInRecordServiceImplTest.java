package com.stayease.stay.service;

import com.stayease.booking.service.GuestProfileService;
import com.stayease.booking.service.ReservationService;
import com.stayease.common.exception.DuplicateResourceException;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.stay.dto.CheckInRecordRequest;
import com.stayease.stay.dto.CheckInRecordResponse;
import com.stayease.stay.entity.CheckInRecord;
import com.stayease.stay.enums.AccessMethod;
import com.stayease.stay.enums.CheckInStatus;
import com.stayease.stay.repository.CheckInRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the stay module's check-in service.
 *
 * The first two tests exist because of a real bug we chased: an operator would
 * choose a status on the check-in screen and the record would save as PENDING
 * anyway. The cause turned out to be in the Angular form, not here — but these
 * tests lock in the backend half of the contract, so next time the answer to
 * "is the server ignoring my status?" is a test run rather than a guess.
 */
@ExtendWith(MockitoExtension.class)
class CheckInRecordServiceImplTest {

    private static final Long RESERVATION_ID = 7L;
    private static final Long GUEST_ID = 42L;

    @Mock
    private CheckInRecordRepository repository;

    @Mock
    private ReservationService reservationService;

    @Mock
    private GuestProfileService guestProfileService;

    @InjectMocks
    private CheckInRecordServiceImpl service;

    private CheckInRecordRequest request(AccessMethod accessMethod, CheckInStatus status, LocalDateTime actual) {
        return new CheckInRecordRequest(RESERVATION_ID, GUEST_ID, actual, accessMethod, true, status);
    }

    private void stubReferencesExist() {
        when(reservationService.existsById(RESERVATION_ID)).thenReturn(true);
        when(guestProfileService.existsById(GUEST_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("create: an explicitly chosen status and access method are saved, not overwritten by defaults")
    void createKeepsTheChosenStatusAndAccessMethod() {
        stubReferencesExist();
        when(repository.existsByReservationId(RESERVATION_ID)).thenReturn(false);
        when(repository.save(any(CheckInRecord.class))).thenAnswer(call -> call.getArgument(0));

        CheckInRecordResponse created =
                service.create(request(AccessMethod.SMART_LOCK, CheckInStatus.CHECKED_IN, null));

        assertThat(created.status()).isEqualTo(CheckInStatus.CHECKED_IN);
        assertThat(created.accessMethod()).isEqualTo(AccessMethod.SMART_LOCK);
        assertThat(created.welcomePackSent()).isTrue();
    }

    @Test
    @DisplayName("create: status defaults to PENDING only when the client sends none")
    void createDefaultsStatusToPending() {
        stubReferencesExist();
        when(repository.existsByReservationId(RESERVATION_ID)).thenReturn(false);
        when(repository.save(any(CheckInRecord.class))).thenAnswer(call -> call.getArgument(0));

        CheckInRecordResponse created = service.create(request(null, null, null));

        assertThat(created.status()).isEqualTo(CheckInStatus.PENDING);
        assertThat(created.accessMethod()).isNull();
    }

    @Test
    @DisplayName("create: the arrival time is stamped automatically when the operator leaves it blank")
    void createStampsArrivalTimeWhenOmitted() {
        stubReferencesExist();
        when(repository.existsByReservationId(RESERVATION_ID)).thenReturn(false);
        when(repository.save(any(CheckInRecord.class))).thenAnswer(call -> call.getArgument(0));

        CheckInRecordResponse created = service.create(request(null, null, null));

        assertThat(created.actualCheckIn()).isNotNull();
    }

    @Test
    @DisplayName("create: a supplied arrival time is respected")
    void createKeepsSuppliedArrivalTime() {
        stubReferencesExist();
        when(repository.existsByReservationId(RESERVATION_ID)).thenReturn(false);
        when(repository.save(any(CheckInRecord.class))).thenAnswer(call -> call.getArgument(0));

        LocalDateTime arrived = LocalDateTime.of(2026, 9, 1, 15, 30);
        CheckInRecordResponse created = service.create(request(null, null, arrived));

        assertThat(created.actualCheckIn()).isEqualTo(arrived);
    }

    @Test
    @DisplayName("create: a reservation can only be checked in once")
    void createRejectsASecondCheckInForTheSameReservation() {
        stubReferencesExist();
        when(repository.existsByReservationId(RESERVATION_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(null, null, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(repository, never()).save(any(CheckInRecord.class));
    }

    @Test
    @DisplayName("create: refuses a check-in for a reservation that doesn't exist")
    void createRequiresARealReservation() {
        when(reservationService.existsById(RESERVATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reservation not found");

        verify(repository, never()).save(any(CheckInRecord.class));
    }

    @Test
    @DisplayName("getById: unknown id gives a not-found error")
    void getByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
