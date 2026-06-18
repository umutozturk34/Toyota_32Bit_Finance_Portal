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

    /**
     * Wires the cache for a single asset type.
     *
     * @param snapshotPrefix key namespace prepended to each asset code
     * @param ttl            expiry applied to every cached entry
     * @param snapshotType   target type used to deserialize cached values
     * @param entityName     human-readable label used in not-found messages and logs
     * @param snapshotFinder source-of-truth loader invoked on a cache miss
     */
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
        // Redis is a read-through optimization, not the source of truth: a Redis outage must not 500
        // valuation reads, so any failure falls through to the DB loader.
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.convertValue(cached, snapshotType);
            }
        } catch (RuntimeException e) {
            log.warn("Redis read failed for {}; falling back to DB loader", cacheKey, e);
        }
        Optional<T> entity = snapshotFinder.apply(key);
        if (entity.isPresent()) {
            T loaded = entity.get();
            // A cache write failure must not discard a successful DB load.
            try {
                redisTemplate.opsForValue().set(cacheKey, loaded, ttl);
            } catch (RuntimeException e) {
                log.warn("Redis write failed for {}; serving DB-loaded value uncached", cacheKey, e);
            }
            return loaded;
        }
        throw new ResourceNotFoundException(entityName + " not found: " + key);
    }

    /** Evicts the cached snapshot for {@code key}; silent when nothing was cached. */
    public void clearCache(String key) {
        String cacheKey = snapshotPrefix + key;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Cleared {} snapshot cache: {}", entityName.toLowerCase(), cacheKey);
        }
    }

    /** Writes (or overwrites) the snapshot for {@code key}, resetting its TTL. */
    public void putSnapshot(String key, T entity) {
        String cacheKey = snapshotPrefix + key;
        redisTemplate.opsForValue().set(cacheKey, entity, ttl);
    }
}
