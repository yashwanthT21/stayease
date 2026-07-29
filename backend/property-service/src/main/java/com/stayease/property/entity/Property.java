package com.stayease.property.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import com.stayease.property.enums.PropertyType;
import com.stayease.property.enums.PropertyStatus;

/**
 * A rentable property listing.
 *
 * ownerId / managerId are plain Long soft references to users in the IAM
 * service — no cross-database foreign key.
 */
@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    private Long managerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PropertyType type;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false)
    private int maxGuests;

    @Column(nullable = false)
    private int bedrooms;

    @Column(nullable = false)
    private int bathrooms;

    @Column(columnDefinition = "TEXT")
    private String amenitiesList;

    @Column(columnDefinition = "TEXT")
    private String houseRules;

    private LocalTime checkInTime;

    private LocalTime checkOutTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PropertyStatus status;

}
