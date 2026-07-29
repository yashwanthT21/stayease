package com.stayease.stay.service;

import com.stayease.stay.dto.GuestReviewRequest;
import com.stayease.stay.dto.GuestReviewResponse;

import java.util.List;

public interface GuestReviewService {

    GuestReviewResponse create(GuestReviewRequest request);

    List<GuestReviewResponse> getAll(Long reservationId, Long guestId);

    GuestReviewResponse getById(Long id);

    GuestReviewResponse update(Long id, GuestReviewRequest request);

    void delete(Long id);
}
