package com.stayease.property.controller;

import com.stayease.property.dto.PricingRuleRequest;
import com.stayease.property.dto.PricingRuleResponse;
import com.stayease.property.service.PricingRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for pricing rules under /api/pricing-rules.
 * Listing is scoped to a property: GET /api/pricing-rules?propertyId=1
 */
@RestController
@RequestMapping("/api/pricing-rules")
public class PricingRuleController {

    private final PricingRuleService service;

    public PricingRuleController(PricingRuleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PricingRuleResponse> create(@Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<PricingRuleResponse>> getByProperty(@RequestParam Long propertyId) {
        return ResponseEntity.ok(service.getByProperty(propertyId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PricingRuleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PricingRuleResponse> update(
            @PathVariable Long id, @Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
