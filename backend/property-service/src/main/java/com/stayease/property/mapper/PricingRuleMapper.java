package com.stayease.property.mapper;

import com.stayease.property.dto.PricingRuleRequest;
import com.stayease.property.dto.PricingRuleResponse;
import com.stayease.property.entity.PricingRule;
import com.stayease.property.enums.PricingRuleStatus;

public final class PricingRuleMapper {

    private PricingRuleMapper() {
    }

    public static PricingRule toEntity(PricingRuleRequest request) {
        PricingRule r = new PricingRule();
        applyRequest(r, request);
        r.setStatus(request.status() != null ? request.status() : PricingRuleStatus.ACTIVE);
        return r;
    }

    public static void updateEntity(PricingRule r, PricingRuleRequest request) {
        applyRequest(r, request);
        if (request.status() != null) {
            r.setStatus(request.status());
        }
    }

    private static void applyRequest(PricingRule r, PricingRuleRequest request) {
        r.setPropertyId(request.propertyId());
        r.setRuleType(request.ruleType());
        r.setStartDate(request.startDate());
        r.setEndDate(request.endDate());
        r.setAdjustment(request.adjustment());
        r.setAdjustmentValue(request.adjustmentValue());
    }

    public static PricingRuleResponse toResponse(PricingRule r) {
        return new PricingRuleResponse(
                r.getId(),
                r.getPropertyId(),
                r.getRuleType(),
                r.getStartDate(),
                r.getEndDate(),
                r.getAdjustment(),
                r.getAdjustmentValue(),
                r.getStatus());
    }
}
