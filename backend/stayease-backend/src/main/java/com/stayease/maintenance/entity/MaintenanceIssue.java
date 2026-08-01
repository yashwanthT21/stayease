package com.stayease.maintenance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.stayease.maintenance.enums.ReportedByType;
import com.stayease.maintenance.enums.MaintenanceCategory;
import com.stayease.maintenance.enums.MaintenancePriority;
import com.stayease.maintenance.enums.MaintenanceStatus;

@Entity
@Table(name = "maintenance_issues")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private Long reportedById;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportedByType reportedByType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenanceCategory category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenancePriority priority;

    private Long assignedContractorId;

    private LocalDateTime reportedDate;

    private LocalDateTime resolvedDate;

    /** Repair cost logged when the issue is resolved; feeds the owner statement. */
    @Column(precision = 12, scale = 2)
    private BigDecimal amountSpent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenanceStatus status;

}
