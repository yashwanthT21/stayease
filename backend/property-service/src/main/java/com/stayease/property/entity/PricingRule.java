package com.stayease.property.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.stayease.property.enums.PricingRuleType;
import com.stayease.property.enums.AdjustmentType;
import com.stayease.property.enums.PricingRuleStatus;

@Entity
@Table(name = "pricing_rules")
@Getter
@Setter
@NoArgsConstructor
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PricingRuleType ruleType;

    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdjustmentType adjustment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal adjustmentValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PricingRuleStatus status;

}
