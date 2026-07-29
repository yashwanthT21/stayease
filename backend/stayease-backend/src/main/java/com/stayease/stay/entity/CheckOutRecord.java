package com.stayease.stay.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.stayease.stay.enums.CheckOutStatus;

@Entity
@Table(name = "check_out_records")
@Getter
@Setter
@NoArgsConstructor
public class CheckOutRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    private LocalDateTime actualCheckOut;

    @Column(nullable = false)
    private boolean damageNoted;

    @Column(columnDefinition = "TEXT")
    private String damageDescription;

    @Column(nullable = false)
    private boolean depositReleased;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckOutStatus status;

}
