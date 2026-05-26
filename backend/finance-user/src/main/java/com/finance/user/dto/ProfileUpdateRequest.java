package com.finance.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(min = 3, max = 32) @Pattern(regexp = "^[a-zA-Z0-9._-]+$") String username,
        @Size(max = 64) String firstName,
        @Size(max = 64) String lastName
) {}
