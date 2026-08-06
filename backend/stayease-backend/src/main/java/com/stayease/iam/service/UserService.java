package com.stayease.iam.service;

import com.stayease.iam.dto.UserRequest;
import com.stayease.iam.dto.UserResponse;
import com.stayease.iam.dto.UserSummaryResponse;

import java.util.List;

/**
 * The business operations available for users. This interface is the
 * "contract": controllers depend on it without caring how it's implemented.
 */
public interface UserService {

    UserResponse create(UserRequest request);

    List<UserResponse> getAll();

    /** Users with the PROPERTY_MANAGER role, for the owner's assignment picker. */
    List<UserResponse> getManagers();

    /** Users with the OWNER role, for Finance's statement-builder picker. */
    List<UserResponse> getOwners();

    /** Users with the HOUSEKEEPING role, for the manager's turnover assignment picker. */
    List<UserResponse> getHousekeepers();

    /**
     * Users with the FINANCE role. Used to notify "Finance" about something that
     * needs their attention — it's a role, not one named person, so whoever holds
     * it gets told.
     */
    List<UserResponse> getFinanceUsers();

    UserResponse getById(Long id);

    /**
     * Name-and-role only, for callers that just need to LABEL a user — including
     * other services (property-service naming an owner in a notification).
     */
    UserSummaryResponse getSummaryById(Long id);

    UserResponse update(Long id, UserRequest request);

    void delete(Long id);

    /**
     * Lightweight existence check used by OTHER modules (e.g. Property) to
     * validate a userId without pulling in the User entity. Keeping this on the
     * service interface is how modules talk to each other cleanly.
     */
    boolean existsById(Long id);
}
