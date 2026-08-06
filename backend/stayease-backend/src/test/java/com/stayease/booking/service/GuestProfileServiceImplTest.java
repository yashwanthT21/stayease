package com.stayease.booking.service;

import com.stayease.booking.dto.GuestProfileRequest;
import com.stayease.booking.dto.GuestProfileResponse;
import com.stayease.booking.entity.GuestProfile;
import com.stayease.booking.enums.GuestStatus;
import com.stayease.booking.enums.VerificationStatus;
import com.stayease.booking.repository.GuestProfileRepository;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.iam.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the booking module's guest-profile service.
 *
 * The important rules here are about TRUST: verification status, review score and
 * booking count are server-owned, so a guest editing their own profile must not be
 * able to promote themselves to "verified" or reset their history. The tests below
 * pin that behaviour down.
 */
@ExtendWith(MockitoExtension.class)
class GuestProfileServiceImplTest {

    @Mock
    private GuestProfileRepository repository;

    @Mock
    private UserService userService;

    @InjectMocks
    private GuestProfileServiceImpl service;

    /** What the guest's own "save my profile" screen sends: no verification, no status. */
    private GuestProfileRequest selfEditRequest() {
        return new GuestProfileRequest(100L, "Asha Nair", "asha@example.com", "9876543210", "Indian", null, null);
    }

    private GuestProfile existingProfile() {
        GuestProfile guest = new GuestProfile();
        guest.setId(1L);
        guest.setUserId(100L);
        guest.setName("Asha Nair");
        guest.setEmail("asha@example.com");
        // Already trusted, with history — none of this may be lost on a self-edit.
        guest.setVerificationStatus(VerificationStatus.TRUSTED);
        guest.setStatus(GuestStatus.ACTIVE);
        guest.setReviewScore(new BigDecimal("4.50"));
        guest.setBookingCount(7);
        return guest;
    }

    @Test
    @DisplayName("create: a new guest starts UNVERIFIED, ACTIVE and with no bookings")
    void createAppliesServerOwnedDefaults() {
        when(userService.existsById(100L)).thenReturn(true);
        when(repository.save(any(GuestProfile.class))).thenAnswer(call -> call.getArgument(0));

        GuestProfileResponse created = service.create(selfEditRequest());

        assertThat(created.verificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(created.status()).isEqualTo(GuestStatus.ACTIVE);
        assertThat(created.bookingCount()).isZero();
        assertThat(created.reviewScore()).isNull();
    }

    @Test
    @DisplayName("create: refuses a profile for a user id that doesn't exist")
    void createRequiresARealUser() {
        when(userService.existsById(100L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(selfEditRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("100");

        verify(repository, never()).save(any(GuestProfile.class));
    }

    @Test
    @DisplayName("update: a guest editing their own details keeps their verification and history")
    void updateDoesNotWipeServerOwnedFields() {
        GuestProfile existing = existingProfile();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(userService.existsById(100L)).thenReturn(true);
        when(repository.save(any(GuestProfile.class))).thenAnswer(call -> call.getArgument(0));

        GuestProfileResponse updated = service.update(1L, selfEditRequest());

        // The request carried no verificationStatus/status, so the stored ones stand.
        assertThat(updated.verificationStatus()).isEqualTo(VerificationStatus.TRUSTED);
        assertThat(updated.status()).isEqualTo(GuestStatus.ACTIVE);
        // And the fields the client can't even send are untouched.
        assertThat(updated.bookingCount()).isEqualTo(7);
        assertThat(updated.reviewScore()).isEqualByComparingTo("4.50");
    }

    @Test
    @DisplayName("update: an admin CAN change verification when they send one")
    void updateAppliesSuppliedVerification() {
        when(repository.findById(1L)).thenReturn(Optional.of(existingProfile()));
        when(userService.existsById(100L)).thenReturn(true);
        when(repository.save(any(GuestProfile.class))).thenAnswer(call -> call.getArgument(0));

        GuestProfileRequest adminEdit = new GuestProfileRequest(
                100L, "Asha Nair", "asha@example.com", null, null,
                VerificationStatus.ID_VERIFIED, GuestStatus.BLACKLISTED);

        GuestProfileResponse updated = service.update(1L, adminEdit);

        assertThat(updated.verificationStatus()).isEqualTo(VerificationStatus.ID_VERIFIED);
        assertThat(updated.status()).isEqualTo(GuestStatus.BLACKLISTED);
    }

    @Test
    @DisplayName("getById: unknown id gives a not-found error")
    void getByIdThrowsWhenMissing() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
