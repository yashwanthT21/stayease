package com.stayease.stay.repository;

import com.stayease.stay.entity.CheckInRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, Long> {

    boolean existsByReservationId(Long reservationId); // one check-in per reservation

    List<CheckInRecord> findByGuestId(Long guestId);
}
