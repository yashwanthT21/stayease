package com.stayease.stay.service;

import com.stayease.stay.dto.CheckInRecordRequest;
import com.stayease.stay.dto.CheckInRecordResponse;

import java.util.List;

public interface CheckInRecordService {

    CheckInRecordResponse create(CheckInRecordRequest request);

    List<CheckInRecordResponse> getAll(Long guestId);

    CheckInRecordResponse getById(Long id);

    CheckInRecordResponse update(Long id, CheckInRecordRequest request);

    void delete(Long id);
}
