package com.stayease.finance.service;

import com.stayease.finance.dto.OwnerPayoutRequest;
import com.stayease.finance.dto.OwnerPayoutResponse;

import java.util.List;

public interface OwnerPayoutService {

    OwnerPayoutResponse create(OwnerPayoutRequest request);

    List<OwnerPayoutResponse> getAll(Long ownerId, Long statementId);

    OwnerPayoutResponse getById(Long id);

    OwnerPayoutResponse update(Long id, OwnerPayoutRequest request);

    void delete(Long id);
}
