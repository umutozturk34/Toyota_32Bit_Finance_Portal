package com.finance.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class MarketCacheService<T, C> {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String snapshotPrefix;
    private final String historyPrefix;
    private final Duration ttl;
    private final Class<T> snapshotType;
    private final TypeReference<List<C>> historyType;
    private final String entityName;
    private final Function<String, Optional<T>> snapshotFinder;
    private final Function<String, List<C>> candleFinder;

    public MarketCacheService(RedisTemplate<String, Object> redisTemplate,
                              ObjectMapper objectMapper,
                              String snapshotPrefix,
                              String historyPrefix,
                              Duration ttl,
                              Class<T> snapshotType,
                              TypeReference<List<C>> historyType,
                              String entityName,
                              Function<String, Optional<T>> snapshotFinder,
                              Function<String, List<C>> candleFinder) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.snapshotPrefix = snapshotPrefix;
        this.historyPrefix = historyPrefix;
        this.ttl = ttl;
        this.snapshotType = snapshotType;
        this.historyType = historyType;
        this.entityName = entityName;
        this.snapshotFinder = snapshotFinder;
        this.candleFinder = candleFinder;
    }

    @Transactional(readOnly = true)
    public T getSnapshot(String key) {
        String cacheKey = snapshotPrefix + key;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Cache HIT (Redis): {}", cacheKey);
            return objectMapper.convertValue(cached, snapshotType);
        }
        log.info("Cache MISS (PostgreSQL): {} - Fetching from DB", cacheKey);
        Optional<T> entity = snapshotFinder.apply(key);
        if (entity.isPresent()) {
            redisTemplate.opsForValue().set(cacheKey, entity.get(), ttl);
            log.info("Cached to Redis: {} (TTL: {})", cacheKey, ttl);
            return entity.get();
        }
        throw new ResourceNotFoundException(entityName + " not found: " + key);
    }

    @Transactional(readOnly = true)
    public List<C> getHistory(String key) {
        String cacheKey = historyPrefix + key;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Cache HIT (Redis): {}", cacheKey);
            return objectMapper.convertValue(cached, historyType);
        }
        log.info("Cache MISS (PostgreSQL): {} - Fetching from DB", cacheKey);
        List<C> candles = candleFinder.apply(key);
        if (!candles.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, candles, ttl);
            log.info("Cached to Redis: {} ({} candles, TTL: {})", cacheKey, candles.size(), ttl);
        }
        return candles;
    }

    public void clearSnapshotCache(String key) {
        String cacheKey = snapshotPrefix + key;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Cleared {} snapshot cache: {}", entityName.toLowerCase(), cacheKey);
        }
    }

    public void clearHistoryCache(String key) {
        String cacheKey = historyPrefix + key;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Cleared {} history cache: {}", entityName.toLowerCase(), cacheKey);
        }
    }

    public void clearCache(String key) {
        clearSnapshotCache(key);
        clearHistoryCache(key);
    }
}
