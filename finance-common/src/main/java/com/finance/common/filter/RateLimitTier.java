package com.finance.common.filter;

import com.finance.common.config.AppProperties;
import io.github.bucket4j.Bandwidth;

/**
 * A named rate-limit policy. Implementations are ordered Spring beans; {@link RateLimitFilter}
 * picks the first whose {@link #matches(String, String)} returns true and applies its
 * {@link #toBandwidth(AppProperties.RateLimit)} bucket, surfacing {@link #errorCode()} and the i18n
 * {@link #errorMessage()} key when the limit is exceeded. More specific tiers must be ordered ahead
 * of broader ones.
 */
public interface RateLimitTier {

    String name();

    boolean matches(String path, String method);

    Bandwidth toBandwidth(AppProperties.RateLimit rl);

    String errorCode();

    /** i18n message key resolved when this tier's limit is exceeded. */
    String errorMessage();
}
