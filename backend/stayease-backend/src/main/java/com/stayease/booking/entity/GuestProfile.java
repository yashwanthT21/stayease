package com.stayease.booking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import com.stayease.booking.enums.VerificationStatus;
import com.stayease.booking.enums.GuestStatus;

@Entity
@Table(name = "guest_profiles")
@Getter
@Setter
@NoArgsConstructor
public class GuestProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 80)
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(precision = 3, scale = 2)
    private BigDecimal reviewScore;

    @Column(nullable = false)
    private int bookingCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuestStatus status;

}
