package com.stayease.finance.enums;

/**
 * The life of an owner statement.
 *
 * DRAFT and ISSUED are Finance's states; APPROVED and REJECTED are the OWNER's
 * answer to an issued statement. That answer is what gates the money: a payout
 * may only be created against an APPROVED statement (see OwnerPayoutServiceImpl),
 * so an owner cannot be paid on figures they never agreed to.
 *
 * REJECTED is not a dead end — Finance corrects the numbers and re-issues, which
 * puts it back to ISSUED for another look. PAID is the terminal state, set once
 * the payout has gone out.
 */
public enum StatementStatus {
    DRAFT,
    ISSUED,
    APPROVED,
    REJECTED,
    PAID
}
