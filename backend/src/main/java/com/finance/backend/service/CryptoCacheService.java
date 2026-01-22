package com.finance.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.repository.CryptoCandleRepository;
import com.finance.backend.repository.CryptoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Cache-Aside pattern for Crypto data - Redis + Database
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final ObjectMapper objectMapper;
    
    private static final String PREFIX_SNAPSHOT = "market:crypto:snapshot:";
    private static final String PREFIX_HISTORY = "market:crypto:history:";
    private static final Duration TTL = Duration.ofHours(24);
    
    /**
     * Get crypto snapshot by ID with cache
     */
    public Crypto getCryptoById(String id) {
        String cacheKey = PREFIX_SNAPSHOT + id;
        
        // 1. Try cache first (hot data)
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("🔥 Cache HIT (Redis): {}", cacheKey);
            // Convert LinkedHashMap to Crypto using ObjectMapper
            return objectMapper.convertValue(cached, Crypto.class);
        }
        
        // 2. Cache miss - fetch from database (cold data)
        log.info("❄️ Cache MISS (PostgreSQL): {} - Fetching from DB", cacheKey);
        Optional<Crypto> crypto = cryptoRepository.findById(id);
        
        if (crypto.isPresent()) {
            // 3. Store in cache for future requests
            redisTemplate.opsForValue().set(cacheKey, crypto.get(), TTL);
            log.info("✅ Cached to Redis: {} (TTL: 24h)", cacheKey);
            return crypto.get();
        }
        
        log.warn("⚠️ Crypto not found: {}", id);
        return null;
    }
    
    /**
     * Get crypto candle history with cache
     */
    @SuppressWarnings("unchecked")
    public List<CryptoCandle> getCandleHistory(String id) {
        String cacheKey = PREFIX_HISTORY + id;
        
        // 1. Try cache first
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("🔥 Cache HIT (Redis): {}", cacheKey);
            // Convert LinkedHashMap list to CryptoCandle list using ObjectMapper
            return objectMapper.convertValue(cached, new TypeReference<List<CryptoCandle>>() {});
        }
        
        // 2. Cache miss - fetch from database
        log.info("❄️ Cache MISS (PostgreSQL): {} - Fetching from DB", cacheKey);
        List<CryptoCandle> candles = cryptoCandleRepository.findByCryptoIdOrderByCandleDateAsc(id);
        
        if (!candles.isEmpty()) {
            // 3. Store in cache
            redisTemplate.opsForValue().set(cacheKey, candles, TTL);
            log.info("✅ Cached to Redis: {} ({} candles, TTL: 24h)", cacheKey, candles.size());
        }
        
        return candles;
    }
    
    /**
     * Clear cache for specific crypto
     */
    public void clearCache(String id) {
        String snapshotKey = PREFIX_SNAPSHOT + id;
        String historyKey = PREFIX_HISTORY + id;
        
        // Delete both keys
        Boolean snapshotDeleted = redisTemplate.delete(snapshotKey);
        Boolean historyDeleted = redisTemplate.delete(historyKey);
        
        log.info("🗑️ Cache cleared for: {} (snapshot: {}, history: {})", 
                id, snapshotDeleted, historyDeleted);
    }
    
    /**
     * Clear all crypto cache
     */
    public void clearAllCache() {
        // Delete all keys with crypto prefix
        var snapshotKeys = redisTemplate.keys(PREFIX_SNAPSHOT + "*");
        var historyKeys = redisTemplate.keys(PREFIX_HISTORY + "*");
        
        int totalDeleted = 0;
        
        if (snapshotKeys != null && !snapshotKeys.isEmpty()) {
            totalDeleted += redisTemplate.delete(snapshotKeys).intValue();
        }
        
        if (historyKeys != null && !historyKeys.isEmpty()) {
            totalDeleted += redisTemplate.delete(historyKeys).intValue();
        }
        
        log.info("🗑️ Cleared all crypto cache: {} keys deleted", totalDeleted);
    }
}
