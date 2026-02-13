package com.finance.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.exception.ResourceNotFoundException;
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
    
    public Crypto getCryptoById(String id) {
        String cacheKey = PREFIX_SNAPSHOT + id;
        
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Cache HIT (Redis): {}", cacheKey);
            return objectMapper.convertValue(cached, Crypto.class);
        }
        
        log.info("Cache MISS (PostgreSQL): {} - Fetching from DB", cacheKey);
        Optional<Crypto> crypto = cryptoRepository.findById(id);
        
        if (crypto.isPresent()) {
            redisTemplate.opsForValue().set(cacheKey, crypto.get(), TTL);
            log.info("Cached to Redis: {} (TTL: 24h)", cacheKey);
            return crypto.get();
        }
        
        throw new ResourceNotFoundException("Crypto not found: " + id);
    }
    
    public List<CryptoCandle> getCandleHistory(String id) {
        String cacheKey = PREFIX_HISTORY + id;
        
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Cache HIT (Redis): {}", cacheKey);
            return objectMapper.convertValue(cached, new TypeReference<List<CryptoCandle>>() {});
        }
        
        log.info("Cache MISS (PostgreSQL): {} - Fetching from DB", cacheKey);
        List<CryptoCandle> candles = cryptoCandleRepository.findByCryptoIdOrderByCandleDateAsc(id);
        
        if (!candles.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, candles, TTL);
            log.info("Cached to Redis: {} ({} candles, TTL: 24h)", cacheKey, candles.size());
        }
        
        return candles;
    }
    
    public void clearSnapshotCache(String cryptoId) {
        String cacheKey = PREFIX_SNAPSHOT + cryptoId;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Cleared crypto snapshot cache: {}", cacheKey);
        }
    }
    
    public void clearHistoryCache(String cryptoId) {
        String cacheKey = PREFIX_HISTORY + cryptoId;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Cleared crypto history cache: {}", cacheKey);
        }
    }
    
    public void clearCache(String cryptoId) {
        clearSnapshotCache(cryptoId);
        clearHistoryCache(cryptoId);
    }
}
