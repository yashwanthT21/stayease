package com.stayease.booking.repository;

import com.stayease.booking.entity.GuestProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuestProfileRepository extends JpaRepository<GuestProfile, Long> {

    List<GuestProfile> findByUserId(Long userId);
}
