package com.stayease.finance.repository;

import com.stayease.finance.entity.OwnerStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OwnerStatementRepository extends JpaRepository<OwnerStatement, Long> {

    List<OwnerStatement> findByOwnerId(Long ownerId);
}
