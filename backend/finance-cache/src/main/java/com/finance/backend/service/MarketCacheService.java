package com.finance.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.exception.ResourceNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

@Log4j2
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
    private final Supplier<List<String>> allKeysFinder;

    public MarketCacheService(RedisTemplate<String, Object> redisTemplate,
                              ObjectMapper objectMapper,
                              String snapshotPrefix,
                              String historyPrefix,
                              Duration ttl,
                              Class<T> snapshotType,
                              TypeReference<List<C>> historyType,
                              String entityName,
                              Function<String, Optional<T>> snapshotFinder,
                              Function<String, List<C>> candleFinder,
                              Supplier<List<String>> allKeysFinder) {
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
        this.allKeysFinder = allKeysFinder;
    }

    @Transactional(readOnly = true)
    public T getSnapshot(String key) {
        String cacheKey = snapshotPrefix + key;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return objectMapper.convertValue(cached, snapshotType);
        }
        Optional<T> entity = snapshotFinder.apply(key);
        if (entity.isPresent()) {
            redisTemplate.opsForValue().set(cacheKey, entity.get(), ttl);
            return entity.get();
        }
        throw new ResourceNotFoundException(entityName + " not found: " + key);
    }

    @Transactional(readOnly = true)
    public List<T> getAllSnapshots() {
        List<String> keys = allKeysFinder.get();
        List<T> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            try {
                result.add(getSnapshot(key));
            } catch (ResourceNotFoundException e) {
                log.debug("{} not found in cache or DB: {}", entityName, key);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<C> getHistory(String key) {
        String cacheKey = historyPrefix + key;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return objectMapper.convertValue(cached, historyType);
        }
        List<C> candles = candleFinder.apply(key);
        if (!candles.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, candles, ttl);
        }
        return candles;
    }

    public void clearSnapshotCache(String key) {
        String cacheKey = snapshotPrefix + key;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Cleared {} snapshot cache: {}", entityName.toLowerCase(), cacheKey);
        }
    }

    public void clearHistoryCache(String key) {
        String cacheKey = historyPrefix + key;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Cleared {} history cache: {}", entityName.toLowerCase(), cacheKey);
        }
    }

    public void clearCache(String key) {
        clearSnapshotCache(key);
        clearHistoryCache(key);
    }

    public void putSnapshot(String key, T entity) {
        String cacheKey = snapshotPrefix + key;
        redisTemplate.opsForValue().set(cacheKey, entity, ttl);
    }

    public void refreshHistory(String key) {
        List<C> candles = candleFinder.apply(key);
        String cacheKey = historyPrefix + key;
        if (!candles.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, candles, ttl);
        } else {
            redisTemplate.delete(cacheKey);
        }
    }
}
