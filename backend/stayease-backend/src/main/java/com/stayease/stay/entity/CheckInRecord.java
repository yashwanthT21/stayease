package com.stayease.stay.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.stayease.stay.enums.AccessMethod;
import com.stayease.stay.enums.CheckInStatus;

@Entity
@Table(name = "check_in_records")
@Getter
@Setter
@NoArgsConstructor
public class CheckInRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private Long guestId;

    private LocalDateTime actualCheckIn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    private AccessMethod accessMethod;

    @Column(nullable = false)
    private boolean welcomePackSent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInStatus status;

}
