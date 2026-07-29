package com.stayease.maintenance.repository;

import com.stayease.maintenance.entity.PreventiveMaintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreventiveMaintenanceRepository extends JpaRepository<PreventiveMaintenance, Long> {

    List<PreventiveMaintenance> findByPropertyId(Long propertyId);
}
