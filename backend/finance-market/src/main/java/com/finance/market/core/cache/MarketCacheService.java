package com.finance.market.core.cache;

import tools.jackson.databind.ObjectMapper;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.core.model.BaseAsset;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Redis read-through snapshot cache for one market's asset type: serves the cached entity, loads
 * and caches it via {@code snapshotFinder} on a miss (throwing if unknown), and supports explicit
 * put/evict. Keys are {@code snapshotPrefix + code}; entries expire after {@code ttl}.
 *
 * @param <T> the market's {@link BaseAsset} entity
 */
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

    /**
     * Returns the cached snapshot, loading and caching it on a miss.
     *
     * @throws ResourceNotFoundException when neither cache nor loader has the asset
     */
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
