package com.stayease.housekeeping.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.LocalDate;
import com.stayease.housekeeping.enums.TurnoverStatus;

@Entity
@Table(name = "turnover_assignments")
@Getter
@Setter
@NoArgsConstructor
public class TurnoverAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    private Long checkOutReservationId;

    private Long checkInReservationId;

    private Long assignedToId;

    private LocalDate assignedDate;

    private LocalDateTime startByTime;

    private LocalDateTime completeByTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TurnoverStatus status;

}
