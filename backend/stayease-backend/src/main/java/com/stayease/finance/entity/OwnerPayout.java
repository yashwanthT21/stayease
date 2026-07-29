package com.stayease.finance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.stayease.finance.enums.PayoutStatus;

@Entity
@Table(name = "owner_payouts")
@Getter
@Setter
@NoArgsConstructor
public class OwnerPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long statementId;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    private LocalDate paymentDate;

    @Column(length = 120)
    private String bankAccountRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status;

}
