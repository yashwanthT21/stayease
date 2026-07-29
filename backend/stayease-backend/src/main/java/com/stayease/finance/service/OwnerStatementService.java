package com.stayease.finance.service;

import com.stayease.finance.dto.OwnerStatementRequest;
import com.stayease.finance.dto.OwnerStatementResponse;

import java.util.List;

public interface OwnerStatementService {

    OwnerStatementResponse create(OwnerStatementRequest request);

    List<OwnerStatementResponse> getAll(Long ownerId);

    OwnerStatementResponse getById(Long id);

    OwnerStatementResponse update(Long id, OwnerStatementRequest request);

    void delete(Long id);

    /** Existence check used by the OwnerPayout sub-module. */
    boolean existsById(Long id);
}
