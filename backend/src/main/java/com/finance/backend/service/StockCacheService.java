package com.finance.backend.service;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.repository.StockRepository;
import com.finance.backend.repository.StockCandleRepository;
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
public class StockCacheService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private static final String SNAPSHOT_KEY_PREFIX = "market:stock:snapshot:";
    private static final String HISTORY_KEY_PREFIX = "market:stock:history:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    @Transactional(readOnly = true)
    public Stock getStockSnapshot(String symbol) {
        String key = SNAPSHOT_KEY_PREFIX + symbol;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof Stock) {
            log.info("Cache HIT (Redis): {}", key);
            return (Stock) cached;
        }
        log.info("Cache MISS (PostgreSQL): {} - Fetching from DB", key);
        Optional<Stock> stock = stockRepository.findById(symbol);
        if (stock.isPresent()) {
            redisTemplate.opsForValue().set(key, stock.get(), CACHE_TTL.toMillis(), TimeUnit.MILLISECONDS);
            log.info("Cached to Redis: {} (TTL: 24h)", key);
            return stock.get();
        }
        throw new ResourceNotFoundException("Stock not found: " + symbol);
    }
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<StockCandle> getStockHistory(String symbol) {
        String key = HISTORY_KEY_PREFIX + symbol;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List) {
            log.info("Cache HIT (Redis): {} candles", key);
            return (List<StockCandle>) cached;
        }
        log.info("Cache MISS (PostgreSQL): {} - Fetching from DB", key);
        List<StockCandle> candles = stockCandleRepository.findByStockSymbolOrderByCandleDateAsc(symbol);
        if (!candles.isEmpty()) {
            redisTemplate.opsForValue().set(key, candles, CACHE_TTL.toMillis(), TimeUnit.MILLISECONDS);
            log.info("Cached to Redis: {} ({} candles, TTL: 24h)", key, candles.size());
        }
        return candles;
    }
    public void clearSnapshotCache(String symbol) {
        String cacheKey = SNAPSHOT_KEY_PREFIX + symbol;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Cleared stock snapshot cache: {}", cacheKey);
        }
    }
    public void clearHistoryCache(String symbol) {
        String cacheKey = HISTORY_KEY_PREFIX + symbol;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Cleared stock history cache: {}", cacheKey);
        }
    }
    public void clearCache(String symbol) {
        clearSnapshotCache(symbol);
        clearHistoryCache(symbol);
    }
}
