package com.stayease.property.repository;

import com.stayease.property.entity.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    List<PricingRule> findByPropertyId(Long propertyId);

    void deleteByPropertyId(Long propertyId);
}
