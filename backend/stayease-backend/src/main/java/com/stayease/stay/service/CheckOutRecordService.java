package com.stayease.stay.service;

import com.stayease.stay.dto.CheckOutRecordRequest;
import com.stayease.stay.dto.CheckOutRecordResponse;

import java.util.List;

public interface CheckOutRecordService {

    CheckOutRecordResponse create(CheckOutRecordRequest request);

    List<CheckOutRecordResponse> getAll();

    CheckOutRecordResponse getById(Long id);

    CheckOutRecordResponse update(Long id, CheckOutRecordRequest request);

    void delete(Long id);
}
