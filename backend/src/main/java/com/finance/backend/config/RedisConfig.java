package com.finance.backend.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {
    
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default config - 24 saat (API çağrılarını minimize et)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24));
        
        // Tüm cache'ler için 24 saat TTL (günde 1 kez güncelleme - API limitlerini korumak için)
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        
        // US Stocks - Günde 1 kez güncelleniyor, TTL 24 saat
        cacheConfigs.put("stocks", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24)));
        
        // BIST Funds (GYO) - Günde 1 kez güncelleniyor, TTL 24 saat
        cacheConfigs.put("bist-funds", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24)));
        
        // Crypto - Günde 1 kez güncelleniyor, TTL 24 saat
        cacheConfigs.put("crypto", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24)));
        
        // Metals - Günde 1 kez güncelleniyor, TTL 24 saat
        cacheConfigs.put("metals", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24)));
        
        // News - Günde 1 kez güncelleniyor, TTL 24 saat
        cacheConfigs.put("news", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24)));
        
        // Exchange rates - Günde bir güncelleniyor, TTL 24 saat
        cacheConfigs.put("exchange-rates", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24)));
        
        // CollectAPI - Günde 1 kez, TTL 24 saat
        cacheConfigs.put("collectapi", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24)));
        
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
