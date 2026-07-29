package com.stayease.booking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.stayease.booking.enums.BookingSource;
import com.stayease.booking.enums.ReservationStatus;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private Long guestId;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;

    @Column(nullable = false)
    private int nights;

    @Column(nullable = false)
    private int guestCount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal baseAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal cleaningFee;

    @Column(precision = 12, scale = 2)
    private BigDecimal serviceFee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingSource bookingSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

}
