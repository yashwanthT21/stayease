package com.stayease.property.repository;

import com.stayease.property.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access for Property.
 *  - findByOwnerId   supports GET /api/properties?ownerId=5   (an owner's listings)
 *  - findByManagerId supports GET /api/properties?managerId=7 (a manager's assigned listings)
 */
@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findByOwnerId(Long ownerId);

    List<Property> findByManagerId(Long managerId);

    List<Property> findByOwnerIdAndManagerId(Long ownerId, Long managerId);
}
