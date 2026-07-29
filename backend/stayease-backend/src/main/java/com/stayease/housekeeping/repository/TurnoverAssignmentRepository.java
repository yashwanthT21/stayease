package com.stayease.housekeeping.repository;

import com.stayease.housekeeping.entity.TurnoverAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TurnoverAssignmentRepository extends JpaRepository<TurnoverAssignment, Long> {

    List<TurnoverAssignment> findByPropertyId(Long propertyId);

    List<TurnoverAssignment> findByAssignedToId(Long assignedToId);
}
