package com.finance.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailChangeConfirmRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "{validation.email.code.sixDigit}") String code
) {
}
