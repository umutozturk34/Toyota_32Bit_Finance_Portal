package com.finance.user.dto;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeInitiateRequest(
        @NotBlank(message = "redirectUri is required")
        String redirectUri
) {
}
