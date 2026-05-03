package com.finance.backend.filter;

import com.finance.backend.config.AppProperties;
import io.github.bucket4j.Bandwidth;

public interface RateLimitTier {

    String name();

    boolean matches(String path, String method);

    Bandwidth toBandwidth(AppProperties.RateLimit rl);

    String errorCode();

    String errorMessage();
}
