package com.stayease.stay.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.stayease.stay.enums.ReviewStatus;

@Entity
@Table(name = "guest_reviews")
@Getter
@Setter
@NoArgsConstructor
public class GuestReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private Long guestId;

    private Integer cleanlinessScore;

    private Integer accuracyScore;

    private Integer locationScore;

    private Integer valueScore;

    @Column(precision = 3, scale = 2)
    private BigDecimal overallScore;

    @Column(columnDefinition = "TEXT")
    private String comments;

    private LocalDateTime submittedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;

}
