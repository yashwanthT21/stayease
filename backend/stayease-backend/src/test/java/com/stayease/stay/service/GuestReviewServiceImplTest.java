package com.stayease.stay.service;

import com.stayease.booking.dto.GuestProfileResponse;
import com.stayease.booking.dto.ReservationResponse;
import com.stayease.booking.enums.BookingSource;
import com.stayease.booking.enums.GuestStatus;
import com.stayease.booking.enums.ReservationStatus;
import com.stayease.booking.enums.VerificationStatus;
import com.stayease.booking.service.GuestProfileService;
import com.stayease.booking.service.ReservationService;
import com.stayease.common.client.NotificationClient;
import com.stayease.common.client.PropertyClient;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.stay.dto.GuestReviewRequest;
import com.stayease.stay.dto.GuestReviewResponse;
import com.stayease.stay.entity.GuestReview;
import com.stayease.stay.enums.ReviewStatus;
import com.stayease.stay.repository.GuestReviewRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the stay module's review service.
 *
 * Two behaviours matter: the overall score is computed by the server (a guest
 * can't post a 5-star average with 1-star details), and posting a review notifies
 * the people accountable for the property — its owner and its manager.
 */
@ExtendWith(MockitoExtension.class)
class GuestReviewServiceImplTest {

    private static final Long RESERVATION_ID = 7L;
    private static final Long GUEST_ID = 42L;
    private static final Long PROPERTY_ID = 3L;
    private static final Long OWNER_USER_ID = 5L;
    private static final Long MANAGER_USER_ID = 6L;

    @Mock
    private GuestReviewRepository repository;

    @Mock
    private ReservationService reservationService;

    @Mock
    private GuestProfileService guestProfileService;

    @Mock
    private PropertyClient propertyClient;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private GuestReviewServiceImpl service;

    // ---------------- fixtures ----------------

    private GuestReviewRequest request(Integer cleanliness, Integer accuracy, Integer location, Integer value) {
        return new GuestReviewRequest(RESERVATION_ID, GUEST_ID, cleanliness, accuracy, location, value,
                "Lovely stay, spotless rooms.", null);
    }

    private ReservationResponse reservation() {
        return new ReservationResponse(RESERVATION_ID, PROPERTY_ID, GUEST_ID,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 3, 2,
                new BigDecimal("9000.00"), null, null, new BigDecimal("9000.00"),
                BookingSource.PLATFORM, ReservationStatus.CHECKED_OUT);
    }

    private GuestProfileResponse guest() {
        return new GuestProfileResponse(GUEST_ID, 100L, "Asha Nair", "asha@example.com",
                null, null, VerificationStatus.ID_VERIFIED, null, 1, GuestStatus.ACTIVE);
    }

    private PropertyClient.PropertySummary property(Long ownerId, Long managerId) {
        return new PropertyClient.PropertySummary(
                PROPERTY_ID, ownerId, managerId, "Sea Breeze Villa", "Kochi", null, null);
    }

    private void stubHappyPath(Long ownerId, Long managerId) {
        when(reservationService.existsById(RESERVATION_ID)).thenReturn(true);
        when(guestProfileService.existsById(GUEST_ID)).thenReturn(true);
        when(repository.save(any(GuestReview.class))).thenAnswer(call -> call.getArgument(0));
        when(reservationService.getById(RESERVATION_ID)).thenReturn(reservation());
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(ownerId, managerId)));
        when(guestProfileService.getById(GUEST_ID)).thenReturn(guest());
    }

    // ---------------- the server owns the score ----------------

    @Test
    @DisplayName("create: overall score is the average of the scores given")
    void createAveragesTheScores() {
        stubHappyPath(OWNER_USER_ID, MANAGER_USER_ID);

        // (5 + 4 + 5 + 2) / 4 = 4.00
        GuestReviewResponse created = service.create(request(5, 4, 5, 2));

        assertThat(created.overallScore()).isEqualByComparingTo("4.00");
        assertThat(created.status()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    @DisplayName("create: partial scores average only what was given")
    void createAveragesOnlySuppliedScores() {
        stubHappyPath(OWNER_USER_ID, MANAGER_USER_ID);

        // Only two scores given: (4 + 5) / 2 = 4.50
        GuestReviewResponse created = service.create(request(4, 5, null, null));

        assertThat(created.overallScore()).isEqualByComparingTo("4.50");
    }

    @Test
    @DisplayName("create: a comment-only review has no overall score rather than a fake zero")
    void createLeavesOverallNullWhenNoScoresGiven() {
        stubHappyPath(OWNER_USER_ID, MANAGER_USER_ID);

        GuestReviewResponse created = service.create(request(null, null, null, null));

        assertThat(created.overallScore()).isNull();
    }

    // ---------------- notifying the property team ----------------

    @Test
    @DisplayName("create: both the owner and the manager are notified about the new review")
    void createNotifiesOwnerAndManager() {
        stubHappyPath(OWNER_USER_ID, MANAGER_USER_ID);

        service.create(request(5, 5, 5, 5));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationClient).notifyUser(eq(OWNER_USER_ID), message.capture(), eq("REVIEW"));
        verify(notificationClient).notifyUser(eq(MANAGER_USER_ID), anyString(), eq("REVIEW"));

        assertThat(message.getValue())
                .contains("New review")
                .contains("Sea Breeze Villa")
                .contains("Asha Nair");
    }

    @Test
    @DisplayName("create: an owner who manages their own property is notified once, not twice")
    void createDeduplicatesWhenOwnerIsAlsoManager() {
        stubHappyPath(OWNER_USER_ID, OWNER_USER_ID);

        service.create(request(4, 4, 4, 4));

        verify(notificationClient, times(1)).notifyUser(eq(OWNER_USER_ID), anyString(), eq("REVIEW"));
    }

    @Test
    @DisplayName("create: the review is still saved when the property can't be resolved")
    void createSurvivesAnUnresolvableProperty() {
        when(reservationService.existsById(RESERVATION_ID)).thenReturn(true);
        when(guestProfileService.existsById(GUEST_ID)).thenReturn(true);
        when(repository.save(any(GuestReview.class))).thenAnswer(call -> call.getArgument(0));
        when(reservationService.getById(RESERVATION_ID)).thenReturn(reservation());
        // property-service is unreachable / the property is gone
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.empty());

        GuestReviewResponse created = service.create(request(5, 5, 5, 5));

        assertThat(created.overallScore()).isEqualByComparingTo("5.00");
        verify(notificationClient, never()).notifyUser(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("create: refuses a review for a reservation that doesn't exist")
    void createRequiresARealReservation() {
        when(reservationService.existsById(RESERVATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(5, 5, 5, 5)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reservation not found");

        verify(repository, never()).save(any(GuestReview.class));
    }
}
