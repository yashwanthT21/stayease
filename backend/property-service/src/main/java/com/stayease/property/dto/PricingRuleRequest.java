package com.stayease.property.dto;

import com.stayease.property.enums.AdjustmentType;
import com.stayease.property.enums.PricingRuleStatus;
import com.stayease.property.enums.PricingRuleType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Incoming JSON for a pricing rule (seasonal rate, weekend surcharge, etc.).
 *
 * adjustment = PERCENT or FIXED; adjustmentValue is the number applied (e.g.
 * 15 with PERCENT = +15%). Negative values are allowed (a discount). The
 * "endDate not before startDate" and "PERCENT not below -100%" rules are checked
 * in the service.
 */
public record PricingRuleRequest(

        @NotNull(message = "propertyId is required")
        Long propertyId,

        @NotNull(message = "ruleType is required")
        PricingRuleType ruleType,

        LocalDate startDate, // optional

        LocalDate endDate,   // optional

        @NotNull(message = "adjustment is required")
        AdjustmentType adjustment,

        @NotNull(message = "adjustmentValue is required")
        BigDecimal adjustmentValue,

        PricingRuleStatus status // optional — defaults to ACTIVE
) {
}
