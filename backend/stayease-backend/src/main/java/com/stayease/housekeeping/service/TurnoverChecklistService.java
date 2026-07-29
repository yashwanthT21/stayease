package com.stayease.housekeeping.service;

import com.stayease.housekeeping.dto.TurnoverChecklistRequest;
import com.stayease.housekeeping.dto.TurnoverChecklistResponse;

import java.util.List;

public interface TurnoverChecklistService {

    TurnoverChecklistResponse create(TurnoverChecklistRequest request);

    List<TurnoverChecklistResponse> getByTurnover(Long turnoverId);

    TurnoverChecklistResponse getById(Long id);

    TurnoverChecklistResponse update(Long id, TurnoverChecklistRequest request);

    void delete(Long id);
}
