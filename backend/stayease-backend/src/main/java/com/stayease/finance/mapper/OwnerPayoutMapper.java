package com.stayease.finance.mapper;

import com.stayease.finance.dto.OwnerPayoutRequest;
import com.stayease.finance.dto.OwnerPayoutResponse;
import com.stayease.finance.entity.OwnerPayout;
import com.stayease.finance.enums.PayoutStatus;

public final class OwnerPayoutMapper {

    private OwnerPayoutMapper() {
    }

    public static OwnerPayout toEntity(OwnerPayoutRequest request) {
        OwnerPayout p = new OwnerPayout();
        p.setStatementId(request.statementId());
        p.setOwnerId(request.ownerId());
        apply(p, request);
        return p;
    }

    public static void updateEntity(OwnerPayout p, OwnerPayoutRequest request) {
        p.setStatementId(request.statementId());
        p.setOwnerId(request.ownerId());
        apply(p, request);
    }

    private static void apply(OwnerPayout p, OwnerPayoutRequest request) {
        p.setAmount(request.amount());
        p.setPaymentDate(request.paymentDate());
        p.setBankAccountRef(request.bankAccountRef());
        p.setStatus(request.status() != null ? request.status() : PayoutStatus.PENDING);
    }

    public static OwnerPayoutResponse toResponse(OwnerPayout p) {
        return new OwnerPayoutResponse(
                p.getId(),
                p.getStatementId(),
                p.getOwnerId(),
                p.getAmount(),
                p.getPaymentDate(),
                p.getBankAccountRef(),
                p.getStatus());
    }
}
