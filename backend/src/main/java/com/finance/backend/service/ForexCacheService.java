package com.finance.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.repository.ForexCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForexCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final ObjectMapper objectMapper;
    
    private static final String PREFIX_SNAPSHOT = "forex:snapshot:";
    private static final String PREFIX_HISTORY = "forex:history:";
    private static final Duration TTL = Duration.ofHours(24);
    
    public Forex getForexSnapshot(String currencyCode) {
        String cacheKey = PREFIX_SNAPSHOT + currencyCode;
        
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Cache HIT (Redis): {}", cacheKey);
            try {
                String json = objectMapper.writeValueAsString(cached);
                Forex forex = objectMapper.readValue(json, Forex.class);
                log.debug("Deserialized from Redis - yahooUpdatedAt: {}, tcmbUpdatedAt: {}", 
                    forex.getYahooUpdatedAt(), forex.getTcmbUpdatedAt());
                return forex;
            } catch (Exception e) {
                log.error("Failed to deserialize cached forex: {}", e.getMessage());
                redisTemplate.delete(cacheKey);
            }
        }
        
        log.info("Cache MISS (PostgreSQL): {} - Fetching from DB", cacheKey);
        Optional<Forex> forex = forexRepository.findByCurrencyCode(currencyCode);
        
        if (forex.isPresent()) {
            redisTemplate.opsForValue().set(cacheKey, forex.get(), TTL);
            log.info("Cached to Redis: {} (TTL: 24h)", cacheKey);
            return forex.get();
        }
        
        throw new ResourceNotFoundException("Forex not found: " + currencyCode);
    }
    
    public List<ForexCandle> getForexHistory(String currencyCode) {
        String cacheKey = PREFIX_HISTORY + currencyCode;
        
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Cache HIT (Redis): {}", cacheKey);
            try {
                String json = objectMapper.writeValueAsString(cached);
                List<ForexCandle> candles = objectMapper.readValue(json, new TypeReference<List<ForexCandle>>() {});
                return candles;
            } catch (Exception e) {
                log.error("Failed to deserialize cached candles: {}", e.getMessage());
                redisTemplate.delete(cacheKey);
            }
        }
        
        log.info("Cache MISS (PostgreSQL): {} - Fetching from DB", cacheKey);
        List<ForexCandle> candles = forexCandleRepository.findTop1825ByCurrencyCodeOrderByCandleDateAsc(currencyCode);
        
        if (!candles.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, candles, TTL);
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
    
    public void clearAllSnapshotCache() {
        List<Forex> allForex = forexRepository.findAll();
        for (Forex forex : allForex) {
            clearSnapshotCache(forex.getCurrencyCode());
        }
        log.info("Cleared ALL forex snapshot caches ({} items)", allForex.size());
    }
    
    public void clearAllHistoryCache() {
        List<Forex> allForex = forexRepository.findAll();
        for (Forex forex : allForex) {
            clearHistoryCache(forex.getCurrencyCode());
        }
        log.info("Cleared ALL forex history caches ({} items)", allForex.size());
    }
}
