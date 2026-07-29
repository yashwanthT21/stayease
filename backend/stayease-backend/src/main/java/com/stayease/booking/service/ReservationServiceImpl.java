package com.stayease.booking.service;

import com.stayease.booking.dto.ReservationRequest;
import com.stayease.booking.dto.ReservationResponse;
import com.stayease.booking.entity.Reservation;
import com.stayease.booking.mapper.ReservationMapper;
import com.stayease.booking.repository.ReservationRepository;
import com.stayease.common.client.PropertyClient;
import com.stayease.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for reservations.
 *
 * Three collaborators by constructor injection:
 *  - ReservationRepository (own data)
 *  - PropertyClient        (validate propertyId — remote call to property-service)
 *  - GuestProfileService   (validate guestId — within the booking module)
 *
 * A reservation can't exist without a real property AND a real guest, so we
 * check both before saving. The dates are validated too, then the mapper
 * computes nights + totalAmount.
 */
@Service
@Transactional
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository repository;
    private final PropertyClient propertyClient;
    private final GuestProfileService guestProfileService;

    public ReservationServiceImpl(ReservationRepository repository,
                                  PropertyClient propertyClient,
                                  GuestProfileService guestProfileService) {
        this.repository = repository;
        this.propertyClient = propertyClient;
        this.guestProfileService = guestProfileService;
    }

    @Override
    public ReservationResponse create(ReservationRequest request) {
        validateReferencesAndDates(request);
        return ReservationMapper.toResponse(repository.save(ReservationMapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getAll(Long propertyId, Long guestId) {
        List<Reservation> reservations;
        if (propertyId != null) {
            reservations = repository.findByPropertyId(propertyId);
        } else if (guestId != null) {
            reservations = repository.findByGuestId(guestId);
        } else {
            reservations = repository.findAll();
        }
        return reservations.stream().map(ReservationMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getById(Long id) {
        return ReservationMapper.toResponse(findOrThrow(id));
    }

    @Override
    public ReservationResponse update(Long id, ReservationRequest request) {
        Reservation reservation = findOrThrow(id);
        validateReferencesAndDates(request);
        ReservationMapper.updateEntity(reservation, request);
        return ReservationMapper.toResponse(repository.save(reservation));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return id != null && repository.existsById(id);
    }

    private Reservation findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found with id " + id));
    }

    /** All the create/update guards in one place. */
    private void validateReferencesAndDates(ReservationRequest request) {
        if (!propertyClient.existsById(request.propertyId())) {
            throw new ResourceNotFoundException("Property not found with id " + request.propertyId());
        }
        if (!guestProfileService.existsById(request.guestId())) {
            throw new ResourceNotFoundException("Guest profile not found with id " + request.guestId());
        }
        // checkOutDate must be strictly after checkInDate (so nights >= 1).
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new IllegalArgumentException("checkOutDate must be after checkInDate");
        }
    }
}
