package com.stayease.property.dto;

import com.stayease.property.enums.AdjustmentType;
import com.stayease.property.enums.PricingRuleStatus;
import com.stayease.property.enums.PricingRuleType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricingRuleResponse(
        Long id,
        Long propertyId,
        PricingRuleType ruleType,
        LocalDate startDate,
        LocalDate endDate,
        AdjustmentType adjustment,
        BigDecimal adjustmentValue,
        PricingRuleStatus status
) {
}
