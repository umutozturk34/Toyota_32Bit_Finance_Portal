package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.common.model.Currency;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Log4j2
@Component
public class BeaterCacheManager {

    private final Cache<String, InflationBeaterResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public InflationBeaterResponse getOrCompute(String key, Supplier<InflationBeaterResponse> loader) {
        InflationBeaterResponse response = cache.get(key, k -> loader.get());
        if (!isWorthCaching(response)) {
            cache.invalidate(key);
        }
        return response;
    }

    public InflationBeaterResponse peek(String key) {
        return cache.getIfPresent(key);
    }

    @Async
    public void warmAsync(String key, String period, String code, Supplier<InflationBeaterResponse> loader) {
        if (cache.getIfPresent(key) != null) return;
        if (!inFlight.add(key)) return;
        try {
            InflationBeaterResponse result = loader.get();
            if (isWorthCaching(result)) {
                cache.put(key, result);
            } else {
                log.info("Beater async warm produced empty result, not caching period={} benchmark={}",
                        period, code);
            }
        } catch (RuntimeException e) {
            log.warn("Beater async warm failed period={} benchmark={}: {}", period, code, e.getMessage());
        } finally {
            inFlight.remove(key);
        }
    }

    public void refresh(String key, String period, String code, Supplier<InflationBeaterResponse> loader) {
        InflationBeaterResponse result = loader.get();
        if (isWorthCaching(result)) {
            cache.put(key, result);
        } else {
            log.info("Beater refresh produced empty result, skipping cache period={} benchmark={}",
                    period, code);
        }
    }

    public void clear() {
        cache.invalidateAll();
    }

    public String buildKey(String period, String code, Currency override) {
        return period + "|" + code + "|" + (override != null ? override.name() : "AUTO");
    }

    public boolean isWorthCaching(InflationBeaterResponse r) {
        return r != null && r.entries() != null && !r.entries().isEmpty();
    }
}
