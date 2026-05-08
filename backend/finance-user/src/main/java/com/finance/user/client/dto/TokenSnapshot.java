package com.finance.user.client.dto;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.time.Instant;

public record TokenSnapshot(String token, Instant expiresAt) {

    public boolean isValid(long safetyMarginSeconds) {
        return Instant.now().isBefore(expiresAt.minusSeconds(safetyMarginSeconds));
    }
}
