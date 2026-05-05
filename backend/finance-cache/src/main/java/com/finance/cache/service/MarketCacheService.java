package com.finance.cache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.BaseAsset;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

@Log4j2
public class MarketCacheService<T extends BaseAsset> {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String snapshotPrefix;
    private final Duration ttl;
    private final Class<T> snapshotType;
    private final String entityName;
    private final Function<String, Optional<T>> snapshotFinder;

    public MarketCacheService(RedisTemplate<String, Object> redisTemplate,
                              ObjectMapper objectMapper,
                              String snapshotPrefix,
                              Duration ttl,
                              Class<T> snapshotType,
                              String entityName,
                              Function<String, Optional<T>> snapshotFinder) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.snapshotPrefix = snapshotPrefix;
        this.ttl = ttl;
        this.snapshotType = snapshotType;
        this.entityName = entityName;
        this.snapshotFinder = snapshotFinder;
    }

    public T getSnapshot(String key) {
        String cacheKey = snapshotPrefix + key;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return objectMapper.convertValue(cached, snapshotType);
        }
        Optional<T> entity = snapshotFinder.apply(key);
        if (entity.isPresent()) {
            T loaded = entity.get();
            redisTemplate.opsForValue().set(cacheKey, loaded, ttl);
            return loaded;
        }
        throw new ResourceNotFoundException(entityName + " not found: " + key);
    }

    public void clearCache(String key) {
        String cacheKey = snapshotPrefix + key;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Cleared {} snapshot cache: {}", entityName.toLowerCase(), cacheKey);
        }
    }

    public void putSnapshot(String key, T entity) {
        String cacheKey = snapshotPrefix + key;
        redisTemplate.opsForValue().set(cacheKey, entity, ttl);
    }
}
