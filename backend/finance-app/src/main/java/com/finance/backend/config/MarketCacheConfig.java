package com.finance.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.model.*;
import com.finance.backend.repository.*;
import com.finance.backend.service.MarketCacheService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

@Configuration
public class MarketCacheConfig {

    private static final Duration TTL = Duration.ofHours(24);

    @Bean
    public MarketCacheService<Crypto> cryptoCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            CryptoRepository cryptoRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                "market:crypto:snapshot:", TTL, Crypto.class, "Crypto",
                cryptoRepository::findById);
    }

    @Bean
    public MarketCacheService<Stock> stockCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            StockRepository stockRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                "market:stock:snapshot:", TTL, Stock.class, "Stock",
                stockRepository::findById);
    }

    @Bean
    public MarketCacheService<Forex> forexCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            ForexRepository forexRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                "market:forex:snapshot:", TTL, Forex.class, "Forex",
                forexRepository::findById);
    }

    @Bean
    public MarketCacheService<Fund> fundCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            FundRepository fundRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                "market:fund:snapshot:", TTL, Fund.class, "Fund",
                fundRepository::findById);
    }

    @Bean
    public MarketCacheService<Commodity> commodityCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            CommodityRepository commodityRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                "market:commodity:snapshot:", TTL, Commodity.class, "Commodity",
                commodityRepository::findById);
    }

    @Bean
    public MarketCacheService<Bond> bondCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            BondRepository bondRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                "market:bond:snapshot:", TTL, Bond.class, "Bond",
                bondRepository::findById);
    }
}
