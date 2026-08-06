package com.stayease.iam.service;

import com.stayease.common.exception.DuplicateResourceException;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.iam.dto.UserRequest;
import com.stayease.iam.dto.UserResponse;
import com.stayease.iam.entity.User;
import com.stayease.iam.enums.UserRole;
import com.stayease.iam.enums.UserStatus;
import com.stayease.iam.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Unit tests for the IAM module's user service.
 *
 * These are UNIT tests: the repository is a Mockito mock, so no database, no
 * Spring context, and the whole class runs in milliseconds. What we're checking
 * is the service's own decisions — the rules that live in Java, not in SQL:
 * unique emails, sensible defaults, and a 404 for an unknown id.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    /** A valid create request, so each test only states what it cares about. */
    private UserRequest request(String email, UserStatus status) {
        return new UserRequest("Asha Nair", email, "9876543210", UserRole.PROPERTY_MANAGER, status);
    }

    private User entity(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setName("Asha Nair");
        user.setEmail(email);
        user.setRole(UserRole.PROPERTY_MANAGER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    @DisplayName("create: saves the user and defaults status to ACTIVE when the client omits it")
    void createDefaultsStatusToActive() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse created = userService.create(request("asha@example.com", null));

        assertThat(created.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.email()).isEqualTo("asha@example.com");

        // Also assert on what actually reached the repository, not just the response.
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("create: keeps an explicitly supplied status")
    void createKeepsSuppliedStatus() {
        when(userRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse created = userService.create(request("asha@example.com", UserStatus.SUSPENDED));

        assertThat(created.status()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("create: rejects a duplicate email and never touches the database")
    void createRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request("taken@example.com", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("taken@example.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("getById: unknown id gives a not-found error (a 404 to the client)")
    void getByIdThrowsWhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("update: changing to an email someone else already uses is rejected")
    void updateRejectsEmailTakenBySomeoneElse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "old@example.com")));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(1L, request("taken@example.com", null)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("update: keeping your own email is allowed (not treated as a duplicate)")
    void updateAllowsKeepingOwnEmail() {
        User existing = entity(1L, "asha@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse updated = userService.update(1L, request("asha@example.com", null));

        assertThat(updated.email()).isEqualTo("asha@example.com");
        // The uniqueness check is skipped entirely when the email hasn't changed.
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("getManagers: asks only for PROPERTY_MANAGER users")
    void getManagersFiltersByRole() {
        when(userRepository.findByRole(UserRole.PROPERTY_MANAGER))
                .thenReturn(List.of(entity(1L, "manager@example.com")));

        List<UserResponse> managers = userService.getManagers();

        assertThat(managers).hasSize(1);
        assertThat(managers.get(0).role()).isEqualTo(UserRole.PROPERTY_MANAGER);
    }

    @Test
    @DisplayName("existsById: null id is false, never a database call")
    void existsByIdHandlesNull() {
        assertThat(userService.existsById(null)).isFalse();
        verify(userRepository, never()).existsById(any());
    }
}
