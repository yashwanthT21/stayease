package com.stayease.maintenance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import com.stayease.maintenance.enums.PreventiveFrequency;
import com.stayease.maintenance.enums.PreventiveStatus;

@Entity
@Table(name = "preventive_maintenance")
@Getter
@Setter
@NoArgsConstructor
public class PreventiveMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false, length = 150)
    private String taskName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PreventiveFrequency frequency;

    private LocalDate nextScheduledDate;

    private LocalDate lastCompletedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PreventiveStatus status;

}
