package com.stayease.housekeeping.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.stayease.housekeeping.enums.ChecklistCategory;
import com.stayease.housekeeping.enums.ChecklistStatus;

@Entity
@Table(name = "turnover_checklists")
@Getter
@Setter
@NoArgsConstructor
public class TurnoverChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long turnoverId;

    @Column(nullable = false, length = 150)
    private String taskName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChecklistCategory category;

    @Column(nullable = false)
    private boolean completed;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChecklistStatus status;

}
