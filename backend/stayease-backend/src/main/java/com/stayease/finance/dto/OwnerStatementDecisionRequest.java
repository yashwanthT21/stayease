package com.stayease.finance.dto;

import jakarta.validation.constraints.Size;

/**
 * The owner's answer to an issued statement.
 *
 * The body is optional (an approval usually needs no words), which is why there is
 * no @NotBlank here — but a REJECTION does: the service requires a note in that
 * case, because "rejected, no reason given" leaves Finance with nothing to correct
 * before re-issuing.
 */
public record OwnerStatementDecisionRequest(

        @Size(max = 500, message = "note must be at most 500 characters")
        String note
) {
}
