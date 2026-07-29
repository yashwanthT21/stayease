package com.stayease.property.repository;

import com.stayease.property.entity.AvailabilityCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access for per-date availability rows.
 *
 * findByPropertyIdAndCalendarDate enforces the "one row per (property, date)"
 * rule in the service with a friendly 409. deleteByPropertyId is used to clean
 * up a property's calendar when the property itself is deleted.
 */
@Repository
public interface AvailabilityCalendarRepository extends JpaRepository<AvailabilityCalendar, Long> {

    List<AvailabilityCalendar> findByPropertyId(Long propertyId);

    Optional<AvailabilityCalendar> findByPropertyIdAndCalendarDate(Long propertyId, LocalDate calendarDate);

    void deleteByPropertyId(Long propertyId);
}
