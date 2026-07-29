package com.stayease.property.service;

import com.stayease.property.dto.PricingRuleRequest;
import com.stayease.property.dto.PricingRuleResponse;

import java.util.List;

public interface PricingRuleService {

    PricingRuleResponse create(PricingRuleRequest request);

    List<PricingRuleResponse> getByProperty(Long propertyId);

    PricingRuleResponse getById(Long id);

    PricingRuleResponse update(Long id, PricingRuleRequest request);

    void delete(Long id);
}
