package com.stayease.iam.mapper;

import com.stayease.iam.dto.UserRequest;
import com.stayease.iam.dto.UserResponse;
import com.stayease.iam.entity.User;
import com.stayease.iam.enums.UserStatus;

/**
 * Translates between the JPA entity (User) and the API DTOs.
 *
 * Keeping this conversion in one place stops the same mapping code from being
 * copy-pasted around the service. These are plain static methods — no Spring
 * needed, nothing to inject.
 */
public final class UserMapper {

    private UserMapper() {
        // utility class — never instantiated
    }

    /** Build a brand-new entity from an incoming create request. */
    public static User toEntity(UserRequest request) {
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(request.role());
        // status is optional on input — default to ACTIVE if the client omits it
        user.setStatus(request.status() != null ? request.status() : UserStatus.ACTIVE);
        return user;
    }

    /** Copy editable fields from an update request onto an existing entity. */
    public static void updateEntity(User user, UserRequest request) {
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRole(request.role());
        if (request.status() != null) {
            user.setStatus(request.status());
        }
    }

    /** Convert an entity into the response DTO we send back to the client. */
    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus());
    }
}
