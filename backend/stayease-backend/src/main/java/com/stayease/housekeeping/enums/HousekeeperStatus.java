package com.stayease.housekeeping.enums;

/**
 * The housekeeper's own view of a turnover: whether they have finished the
 * clean. Set by the assigned housekeeper — distinct from the turnover's overall
 * (manager) status, which the manager sets after verifying the work.
 */
public enum HousekeeperStatus {
    PENDING,
    COMPLETED
}
