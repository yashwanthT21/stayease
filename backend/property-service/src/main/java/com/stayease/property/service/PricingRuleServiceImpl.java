package com.stayease.property.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.property.dto.PricingRuleRequest;
import com.stayease.property.dto.PricingRuleResponse;
import com.stayease.property.entity.PricingRule;
import com.stayease.property.enums.AdjustmentType;
import com.stayease.property.mapper.PricingRuleMapper;
import com.stayease.property.repository.PricingRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class PricingRuleServiceImpl implements PricingRuleService {

    /** A PERCENT adjustment below -100% would drive the price negative. */
    private static final BigDecimal MIN_PERCENT = new BigDecimal("-100");

    private final PricingRuleRepository repository;
    private final PropertyService propertyService;

    public PricingRuleServiceImpl(PricingRuleRepository repository, PropertyService propertyService) {
        this.repository = repository;
        this.propertyService = propertyService;
    }

    @Override
    public PricingRuleResponse create(PricingRuleRequest request) {
        ensurePropertyExists(request.propertyId());
        validate(request);
        return PricingRuleMapper.toResponse(repository.save(PricingRuleMapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PricingRuleResponse> getByProperty(Long propertyId) {
        ensurePropertyExists(propertyId);
        return repository.findByPropertyId(propertyId)
                .stream().map(PricingRuleMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PricingRuleResponse getById(Long id) {
        return PricingRuleMapper.toResponse(findOrThrow(id));
    }

    @Override
    public PricingRuleResponse update(Long id, PricingRuleRequest request) {
        PricingRule entity = findOrThrow(id);
        ensurePropertyExists(request.propertyId());
        validate(request);
        PricingRuleMapper.updateEntity(entity, request);
        return PricingRuleMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private PricingRule findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pricing rule not found with id " + id));
    }

    private void ensurePropertyExists(Long propertyId) {
        if (!propertyService.existsById(propertyId)) {
            throw new ResourceNotFoundException("Property not found with id " + propertyId);
        }
    }

    /**
     * Business rules that a single-field annotation can't express:
     *  - when both dates are present, endDate cannot be before startDate;
     *  - a PERCENT adjustment cannot be below -100% (that would make the price
     *    negative). Both throw IllegalArgumentException -> HTTP 400.
     */
    private void validate(PricingRuleRequest request) {
        if (request.startDate() != null
                && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        if (request.adjustment() == AdjustmentType.PERCENT
                && request.adjustmentValue().compareTo(MIN_PERCENT) < 0) {
            throw new IllegalArgumentException(
                    "A PERCENT adjustmentValue cannot be less than -100 (that would make the price negative)");
        }
    }
}
