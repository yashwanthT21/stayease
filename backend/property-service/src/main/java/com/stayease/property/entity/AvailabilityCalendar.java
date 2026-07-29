package com.stayease.property.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import com.stayease.property.enums.AvailabilityStatus;

@Entity
@Table(name = "availability_calendars")
@Getter
@Setter
@NoArgsConstructor
public class AvailabilityCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private LocalDate calendarDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilityStatus availabilityStatus;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(nullable = false)
    private int minimumNights;

    private LocalDateTime lastUpdated;

}
