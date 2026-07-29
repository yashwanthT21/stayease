package com.stayease.booking.service;

import com.stayease.booking.dto.ReservationRequest;
import com.stayease.booking.dto.ReservationResponse;

import java.util.List;

public interface ReservationService {

    ReservationResponse create(ReservationRequest request);

    /** Optional filters: by propertyId, or by guestId, or neither (all). */
    List<ReservationResponse> getAll(Long propertyId, Long guestId);

    ReservationResponse getById(Long id);

    ReservationResponse update(Long id, ReservationRequest request);

    /** Manager approves a PENDING reservation: holds the dates + CONFIRMED. */
    ReservationResponse approve(Long id);

    /** Manager rejects a PENDING reservation: CANCELLED (dates stay free). */
    ReservationResponse reject(Long id);

    void delete(Long id);

    /** Existence check used by the Stay and Housekeeping modules. */
    boolean existsById(Long id);
}
