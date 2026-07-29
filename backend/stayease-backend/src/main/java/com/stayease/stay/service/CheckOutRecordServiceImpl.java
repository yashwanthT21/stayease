package com.stayease.stay.service;

import com.stayease.booking.service.ReservationService;
import com.stayease.common.exception.DuplicateResourceException;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.stay.dto.CheckOutRecordRequest;
import com.stayease.stay.dto.CheckOutRecordResponse;
import com.stayease.stay.entity.CheckOutRecord;
import com.stayease.stay.mapper.CheckOutRecordMapper;
import com.stayease.stay.repository.CheckOutRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CheckOutRecordServiceImpl implements CheckOutRecordService {

    private final CheckOutRecordRepository repository;
    private final ReservationService reservationService;

    public CheckOutRecordServiceImpl(CheckOutRecordRepository repository,
                                     ReservationService reservationService) {
        this.repository = repository;
        this.reservationService = reservationService;
    }

    @Override
    public CheckOutRecordResponse create(CheckOutRecordRequest request) {
        ensureReservationExists(request.reservationId());
        if (repository.existsByReservationId(request.reservationId())) {
            throw new DuplicateResourceException(
                    "A check-out record already exists for reservation " + request.reservationId());
        }
        return CheckOutRecordMapper.toResponse(repository.save(CheckOutRecordMapper.toEntity(request)));
    }


    @Transactional(readOnly = true)
    public List<CheckOutRecordResponse> getAll() {
        return repository.findAll().stream().map(CheckOutRecordMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckOutRecordResponse getById(Long id) {
        return CheckOutRecordMapper.toResponse(findOrThrow(id));
    }

    @Override
    public CheckOutRecordResponse update(Long id, CheckOutRecordRequest request) {
        CheckOutRecord entity = findOrThrow(id);
        ensureReservationExists(request.reservationId());
        CheckOutRecordMapper.updateEntity(entity, request);
        return CheckOutRecordMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private CheckOutRecord findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Check-out record not found with id " + id));
    }

    private void ensureReservationExists(Long reservationId) {
        if (!reservationService.existsById(reservationId)) {
            throw new ResourceNotFoundException("Reservation not found with id " + reservationId);
        }
    }
}
