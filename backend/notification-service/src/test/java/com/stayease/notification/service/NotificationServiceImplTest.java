package com.stayease.notification.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.notification.dto.NotificationRequest;
import com.stayease.notification.dto.NotificationResponse;
import com.stayease.notification.entity.Notification;
import com.stayease.notification.enums.NotificationCategory;
import com.stayease.notification.enums.NotificationStatus;
import com.stayease.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for notification-service.
 *
 * The interesting part of this service is its FILTERING, because the list endpoint
 * accepts userId and status independently. An early version let a status-only
 * filter fall through to findAll() and silently returned everything — the
 * "filters by status alone" test below is the regression guard for that bug.
 *
 * markAsRead / dismiss are also worth a test each: they're the two transitions a
 * user performs constantly, and they must be idempotent (marking an already-read
 * notification read again is not an error).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final Long USER_ID = 100L;

    @Mock
    private NotificationRepository repository;

    @InjectMocks
    private NotificationServiceImpl service;

    private NotificationRequest request(NotificationStatus status) {
        return new NotificationRequest(USER_ID, "Booking confirmed for Sea Breeze Villa.",
                NotificationCategory.BOOKING, status);
    }

    private Notification entity(Long id, NotificationStatus status) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId(USER_ID);
        notification.setMessage("Booking confirmed for Sea Breeze Villa.");
        notification.setCategory(NotificationCategory.BOOKING);
        notification.setStatus(status);
        return notification;
    }

    // ---------------- create ----------------

    @Test
    @DisplayName("create: a new notification is UNREAD and time-stamped by the server")
    void createDefaultsToUnread() {
        when(repository.save(any(Notification.class))).thenAnswer(call -> call.getArgument(0));

        NotificationResponse created = service.create(request(null));

        assertThat(created.status()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(created.createdDate()).isNotNull();
        assertThat(created.category()).isEqualTo(NotificationCategory.BOOKING);
    }

    // ---------------- filtering ----------------

    @Test
    @DisplayName("getAll: userId + status together query on both")
    void getAllFiltersByUserAndStatus() {
        when(repository.findByUserIdAndStatus(USER_ID, NotificationStatus.UNREAD))
                .thenReturn(List.of(entity(1L, NotificationStatus.UNREAD)));

        List<NotificationResponse> found = service.getAll(USER_ID, NotificationStatus.UNREAD);

        assertThat(found).hasSize(1);
        verify(repository, never()).findAll();
    }

    @Test
    @DisplayName("getAll: userId alone scopes to that user's inbox")
    void getAllFiltersByUser() {
        when(repository.findByUserId(USER_ID))
                .thenReturn(List.of(entity(1L, NotificationStatus.UNREAD), entity(2L, NotificationStatus.READ)));

        assertThat(service.getAll(USER_ID, null)).hasSize(2);
        verify(repository, never()).findAll();
    }

    @Test
    @DisplayName("getAll: a status filter on its own really filters (regression: it used to be ignored)")
    void getAllFiltersByStatusAlone() {
        when(repository.findByStatus(NotificationStatus.DISMISSED))
                .thenReturn(List.of(entity(3L, NotificationStatus.DISMISSED)));

        List<NotificationResponse> found = service.getAll(null, NotificationStatus.DISMISSED);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).status()).isEqualTo(NotificationStatus.DISMISSED);
        // The bug was that this fell through to findAll() and returned everything.
        verify(repository, never()).findAll();
    }

    @Test
    @DisplayName("getAll: no filters returns everything")
    void getAllUnfilteredReturnsEverything() {
        when(repository.findAll())
                .thenReturn(List.of(entity(1L, NotificationStatus.UNREAD), entity(2L, NotificationStatus.READ)));

        assertThat(service.getAll(null, null)).hasSize(2);
    }

    // ---------------- transitions ----------------

    @Test
    @DisplayName("markAsRead: moves an unread notification to READ")
    void markAsReadSetsRead() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, NotificationStatus.UNREAD)));
        when(repository.save(any(Notification.class))).thenAnswer(call -> call.getArgument(0));

        NotificationResponse updated = service.markAsRead(1L);

        assertThat(updated.status()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    @DisplayName("markAsRead: marking an already-read notification again is harmless")
    void markAsReadIsIdempotent() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, NotificationStatus.READ)));
        when(repository.save(any(Notification.class))).thenAnswer(call -> call.getArgument(0));

        NotificationResponse updated = service.markAsRead(1L);

        assertThat(updated.status()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    @DisplayName("dismiss: moves a notification to DISMISSED")
    void dismissSetsDismissed() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, NotificationStatus.UNREAD)));
        when(repository.save(any(Notification.class))).thenAnswer(call -> call.getArgument(0));

        NotificationResponse updated = service.dismiss(1L);

        assertThat(updated.status()).isEqualTo(NotificationStatus.DISMISSED);
    }

    @Test
    @DisplayName("markAsRead: an unknown id gives a not-found error")
    void markAsReadThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("update: keeps the original created date (it isn't re-stamped)")
    void updateKeepsCreatedDate() {
        Notification existing = entity(1L, NotificationStatus.UNREAD);
        existing.setCreatedDate(java.time.LocalDateTime.of(2026, 8, 1, 9, 0));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Notification.class))).thenAnswer(call -> call.getArgument(0));

        NotificationResponse updated = service.update(1L, request(NotificationStatus.READ));

        assertThat(updated.createdDate()).isEqualTo(java.time.LocalDateTime.of(2026, 8, 1, 9, 0));
        assertThat(updated.status()).isEqualTo(NotificationStatus.READ);
    }
}
