package com.stayease.maintenance.repository;

import com.stayease.maintenance.entity.MaintenanceIssue;
import com.stayease.maintenance.enums.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceIssueRepository extends JpaRepository<MaintenanceIssue, Long> {

    List<MaintenanceIssue> findByPropertyId(Long propertyId);

    List<MaintenanceIssue> findByStatus(MaintenanceStatus status);
}
