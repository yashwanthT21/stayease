package com.stayease.booking.repository;

import com.stayease.booking.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByPropertyId(Long propertyId);

    List<Reservation> findByGuestId(Long guestId);
}
