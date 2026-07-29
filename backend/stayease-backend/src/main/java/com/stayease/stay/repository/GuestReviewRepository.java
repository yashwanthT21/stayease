package com.stayease.stay.repository;

import com.stayease.stay.entity.GuestReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuestReviewRepository extends JpaRepository<GuestReview, Long> {

    List<GuestReview> findByReservationId(Long reservationId);

    List<GuestReview> findByGuestId(Long guestId);
}
