package com.finance.backend.service;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.repository.ForexCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
@Slf4j
@Service
@RequiredArgsConstructor
public class ForexCacheService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private static final String PREFIX_SNAPSHOT = "forex:snapshot:";
    private static final String PREFIX_HISTORY = "forex:history:";
    private static final Duration TTL = Duration.ofHours(24);
    @Transactional(readOnly = true)
    public Forex getForexSnapshot(String currencyCode) {
        String cacheKey = PREFIX_SNAPSHOT + currencyCode;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Forex) {
            log.info("Cache HIT (Redis): {}", cacheKey);
            return (Forex) cached;
        }
        log.info("Cache MISS (PostgreSQL): {} - Fetching from DB", cacheKey);
        Optional<Forex> forex = forexRepository.findById(currencyCode);
        if (forex.isPresent()) {
            redisTemplate.opsForValue().set(cacheKey, forex.get(), TTL.toMillis(), TimeUnit.MILLISECONDS);
            log.info("Cached to Redis: {} (TTL: 24h)", cacheKey);
            return forex.get();
        }
        throw new ResourceNotFoundException("Forex not found: " + currencyCode);
    }
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ForexCandle> getForexHistory(String currencyCode) {
        String cacheKey = PREFIX_HISTORY + currencyCode;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List) {
            log.info("Cache HIT (Redis): {}", cacheKey);
            return (List<ForexCandle>) cached;
        }
        log.info("Cache MISS (PostgreSQL): {} - Fetching from DB", cacheKey);
        List<ForexCandle> candles = forexCandleRepository.findTop1825ByCurrencyCodeOrderByCandleDateAsc(currencyCode);
        if (!candles.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, candles, TTL.toMillis(), TimeUnit.MILLISECONDS);
            log.info("Cached to Redis: {} ({} candles, TTL: 24h)", cacheKey, candles.size());
        }
        return candles;
    }
    public void clearSnapshotCache(String currencyCode) {
        String cacheKey = PREFIX_SNAPSHOT + currencyCode;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Cleared forex snapshot cache: {}", cacheKey);
        }
    }
    public void clearHistoryCache(String currencyCode) {
        String cacheKey = PREFIX_HISTORY + currencyCode;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Cleared forex history cache: {}", cacheKey);
        }
    }
    public void clearCache(String currencyCode) {
        clearSnapshotCache(currencyCode);
        clearHistoryCache(currencyCode);
    }
}
