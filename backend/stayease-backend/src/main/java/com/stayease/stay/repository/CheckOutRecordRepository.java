package com.stayease.stay.repository;

import com.stayease.stay.entity.CheckOutRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckOutRecordRepository extends JpaRepository<CheckOutRecord, Long> {

    boolean existsByReservationId(Long reservationId); // one check-out per reservation
}
