package com.stayease.booking.service;

import com.stayease.booking.dto.GuestProfileRequest;
import com.stayease.booking.dto.GuestProfileResponse;

import java.util.List;

public interface GuestProfileService {

    GuestProfileResponse create(GuestProfileRequest request);

    List<GuestProfileResponse> getAll(Long userId);

    GuestProfileResponse getById(Long id);

    GuestProfileResponse update(Long id, GuestProfileRequest request);

    void delete(Long id);

    /** Existence check used by the Reservation module to validate guestId. */
    boolean existsById(Long id);
}
