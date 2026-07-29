package com.stayease.finance.repository;

import com.stayease.finance.entity.OwnerPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OwnerPayoutRepository extends JpaRepository<OwnerPayout, Long> {

    List<OwnerPayout> findByOwnerId(Long ownerId);

    List<OwnerPayout> findByStatementId(Long statementId);
}
