package com.stayease.finance.service;

import com.stayease.finance.dto.OwnerStatementDecisionRequest;
import com.stayease.finance.dto.OwnerStatementRequest;
import com.stayease.finance.dto.OwnerStatementResponse;

import java.util.List;

public interface OwnerStatementService {

    OwnerStatementResponse create(OwnerStatementRequest request);

    List<OwnerStatementResponse> getAll(Long ownerId);

    OwnerStatementResponse getById(Long id);

    OwnerStatementResponse update(Long id, OwnerStatementRequest request);

    /**
     * The owner accepts the statement's figures. Only then may Finance create a
     * payout against it (see OwnerPayoutService).
     */
    OwnerStatementResponse approve(Long id, OwnerStatementDecisionRequest request);

    /**
     * The owner disputes the figures. Finance is notified with the owner's reason
     * so they can correct and re-issue; payout stays blocked meanwhile.
     */
    OwnerStatementResponse reject(Long id, OwnerStatementDecisionRequest request);

    /** True when a payout is allowed against this statement (owner has approved). */
    boolean isApproved(Long id);

    void delete(Long id);

    /** Existence check used by the OwnerPayout sub-module. */
    boolean existsById(Long id);
}
