package com.stayease.iam.service;

import com.stayease.iam.dto.UserRequest;
import com.stayease.iam.dto.UserResponse;

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

    UserResponse getById(Long id);

    UserResponse update(Long id, UserRequest request);

    void delete(Long id);

    /**
     * Lightweight existence check used by OTHER modules (e.g. Property) to
     * validate a userId without pulling in the User entity. Keeping this on the
     * service interface is how modules talk to each other cleanly.
     */
    boolean existsById(Long id);
}
