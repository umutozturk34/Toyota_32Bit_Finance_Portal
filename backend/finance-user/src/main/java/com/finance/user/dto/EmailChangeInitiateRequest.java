package com.finance.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Starts an email change by requesting verification of the desired new address. */
public record EmailChangeInitiateRequest(
        @NotBlank @Email @Size(max = 255) String newEmail
) {
}
