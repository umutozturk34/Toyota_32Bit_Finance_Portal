package com.finance.market.core.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Exposes whether the CBRT EVDS API key is configured (the {@code EVDS_API_KEY} environment variable). The
 * EVDS-backed asset classes (forex, bond, macro) cannot fetch anything without it, so callers — notably the
 * cold-start initializer — can surface a clear, actionable "API key missing" state instead of attempting a
 * request that is doomed to fail generically (or only time out).
 */
@Component
public class EvdsCredentials {

    private final boolean configured;

    public EvdsCredentials(@Value("${EVDS_API_KEY:}") String apiKey) {
        this.configured = apiKey != null && !apiKey.isBlank();
    }

    /** @return whether a non-blank EVDS API key is present. */
    public boolean isConfigured() {
        return configured;
    }
}
