package com.stayease.finance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.stayease.finance.enums.StatementStatus;

@Entity
@Table(name = "owner_statements")
@Getter
@Setter
@NoArgsConstructor
public class OwnerStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 20)
    private String period;

    @Column(precision = 14, scale = 2)
    private BigDecimal grossRevenue;

    @Column(precision = 12, scale = 2)
    private BigDecimal platformFee;

    @Column(precision = 12, scale = 2)
    private BigDecimal managementFee;

    @Column(precision = 12, scale = 2)
    private BigDecimal cleaningRevenue;

    @Column(precision = 12, scale = 2)
    private BigDecimal maintenanceCost;

    @Column(precision = 14, scale = 2)
    private BigDecimal netPayout;

    private LocalDateTime generatedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatementStatus status;

    /**
     * The owner's comment on their approve/reject decision. Mainly carries a
     * rejection reason, so Finance knows what to change before re-issuing.
     */
    @Column(length = 500)
    private String ownerNote;

    /** When the owner approved or rejected. Null until they've answered. */
    private LocalDateTime decidedDate;

}
