package com.stayease.housekeeping.repository;

import com.stayease.housekeeping.entity.TurnoverChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TurnoverChecklistRepository extends JpaRepository<TurnoverChecklist, Long> {

    List<TurnoverChecklist> findByTurnoverId(Long turnoverId);
}
