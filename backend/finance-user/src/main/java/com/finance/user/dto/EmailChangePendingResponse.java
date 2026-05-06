package com.finance.user.dto;

import java.time.OffsetDateTime;

public record EmailChangePendingResponse(
        String newEmail,
        OffsetDateTime expiresAt
) {
}
