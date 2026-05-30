package com.finance.user.dto;

import java.time.OffsetDateTime;

/** Reports an outstanding email-change request: the target address and when its verification code expires. */
public record EmailChangePendingResponse(
        String newEmail,
        OffsetDateTime expiresAt
) {
}
