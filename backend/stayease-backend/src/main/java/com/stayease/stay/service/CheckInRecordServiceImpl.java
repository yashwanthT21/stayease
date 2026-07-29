package com.stayease.stay.service;

import com.stayease.booking.service.GuestProfileService;
import com.stayease.booking.service.ReservationService;
import com.stayease.common.exception.DuplicateResourceException;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.stay.dto.CheckInRecordRequest;
import com.stayease.stay.dto.CheckInRecordResponse;
import com.stayease.stay.entity.CheckInRecord;
import com.stayease.stay.mapper.CheckInRecordMapper;
import com.stayease.stay.repository.CheckInRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CheckInRecordServiceImpl implements CheckInRecordService {

    private final CheckInRecordRepository repository;
    private final ReservationService reservationService;
    private final GuestProfileService guestProfileService;

    public CheckInRecordServiceImpl(CheckInRecordRepository repository,
                                    ReservationService reservationService,
                                    GuestProfileService guestProfileService) {
        this.repository = repository;
        this.reservationService = reservationService;
        this.guestProfileService = guestProfileService;
    }

    @Override
    public CheckInRecordResponse create(CheckInRecordRequest request) {
        validateReferences(request);
        if (repository.existsByReservationId(request.reservationId())) {
            throw new DuplicateResourceException(
                    "A check-in record already exists for reservation " + request.reservationId());
        }

        CheckInRecord entity = CheckInRecordMapper.toEntity(request);

        //  AUTO SET CURRENT TIME
        if (entity.getActualCheckIn() == null) {
            entity.setActualCheckIn(java.time.LocalDateTime.now());
        }

        //  AUTO SET STATUS
        if (entity.getStatus() == null) {
            entity.setStatus(com.stayease.stay.enums.CheckInStatus.PENDING);
        }

        return CheckInRecordMapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckInRecordResponse> getAll(Long guestId) {
        List<CheckInRecord> records = (guestId == null)
                ? repository.findAll()
                : repository.findByGuestId(guestId);
        return records.stream().map(CheckInRecordMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckInRecordResponse getById(Long id) {
        return CheckInRecordMapper.toResponse(findOrThrow(id));
    }

    @Override
    public CheckInRecordResponse update(Long id, CheckInRecordRequest request) {
        CheckInRecord entity = findOrThrow(id);
        validateReferences(request);
        CheckInRecordMapper.updateEntity(entity, request);
        return CheckInRecordMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private CheckInRecord findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Check-in record not found with id " + id));
    }

    private void validateReferences(CheckInRecordRequest request) {
        if (!reservationService.existsById(request.reservationId())) {
            throw new ResourceNotFoundException("Reservation not found with id " + request.reservationId());
        }
        if (!guestProfileService.existsById(request.guestId())) {
            throw new ResourceNotFoundException("Guest profile not found with id " + request.guestId());
        }
    }
}
