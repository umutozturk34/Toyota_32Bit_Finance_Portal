package com.finance.user.client.dto;

import java.time.Instant;

/** A cached admin access token plus its expiry, used by the token manager to avoid re-authenticating on every call. */
public record TokenSnapshot(String token, Instant expiresAt) {

    /** True when the token is still usable, treating it as expired {@code safetyMarginSeconds} early to avoid races near the boundary. */
    public boolean isValid(long safetyMarginSeconds) {
        return Instant.now().isBefore(expiresAt.minusSeconds(safetyMarginSeconds));
    }
}
