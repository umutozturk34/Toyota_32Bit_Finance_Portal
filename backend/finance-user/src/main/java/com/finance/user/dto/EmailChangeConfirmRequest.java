package com.finance.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Confirms a pending email change by submitting the six-digit verification code sent to the new address. */
public record EmailChangeConfirmRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "{validation.email.code.sixDigit}") String code
) {
}
