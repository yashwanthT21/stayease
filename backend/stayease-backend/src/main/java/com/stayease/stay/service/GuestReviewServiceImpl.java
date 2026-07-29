package com.stayease.stay.service;

import com.stayease.booking.service.GuestProfileService;
import com.stayease.booking.service.ReservationService;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.stay.dto.GuestReviewRequest;
import com.stayease.stay.dto.GuestReviewResponse;
import com.stayease.stay.entity.GuestReview;
import com.stayease.stay.mapper.GuestReviewMapper;
import com.stayease.stay.repository.GuestReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class GuestReviewServiceImpl implements GuestReviewService {

    private final GuestReviewRepository repository;
    private final ReservationService reservationService;
    private final GuestProfileService guestProfileService;

    public GuestReviewServiceImpl(GuestReviewRepository repository,
                                  ReservationService reservationService,
                                  GuestProfileService guestProfileService) {
        this.repository = repository;
        this.reservationService = reservationService;
        this.guestProfileService = guestProfileService;
    }

    @Override
    public GuestReviewResponse create(GuestReviewRequest request) {
        validateReferences(request);
        return GuestReviewMapper.toResponse(repository.save(GuestReviewMapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuestReviewResponse> getAll(Long reservationId, Long guestId) {
        List<GuestReview> reviews;
        if (reservationId != null) {
            reviews = repository.findByReservationId(reservationId);
        } else if (guestId != null) {
            reviews = repository.findByGuestId(guestId);
        } else {
            reviews = repository.findAll();
        }
        return reviews.stream().map(GuestReviewMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GuestReviewResponse getById(Long id) {
        return GuestReviewMapper.toResponse(findOrThrow(id));
    }

    @Override
    public GuestReviewResponse update(Long id, GuestReviewRequest request) {
        GuestReview entity = findOrThrow(id);
        validateReferences(request);
        GuestReviewMapper.updateEntity(entity, request);
        return GuestReviewMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private GuestReview findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Guest review not found with id " + id));
    }

    private void validateReferences(GuestReviewRequest request) {
        if (!reservationService.existsById(request.reservationId())) {
            throw new ResourceNotFoundException("Reservation not found with id " + request.reservationId());
        }
        if (!guestProfileService.existsById(request.guestId())) {
            throw new ResourceNotFoundException("Guest profile not found with id " + request.guestId());
        }
    }
}
