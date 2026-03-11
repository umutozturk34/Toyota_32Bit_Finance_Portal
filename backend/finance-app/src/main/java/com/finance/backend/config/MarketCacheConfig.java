package com.finance.backend.config;
import com.fasterxml.jackson.core.type.TypeReference;
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

    @Bean
    public MarketCacheService<Crypto, CryptoCandle> cryptoCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            CryptoRepository cryptoRepository,
            CryptoCandleRepository cryptoCandleRepository) {
        return new MarketCacheService<>(
                redisTemplate, objectMapper,
                "market:crypto:snapshot:", "market:crypto:history:",
                Duration.ofHours(24), Crypto.class,
                new TypeReference<>() {}, "Crypto",
                cryptoRepository::findById,
                cryptoCandleRepository::findByCryptoIdOrderByCandleDateAsc,
                cryptoRepository::findAllIds
        );
    }

    @Bean
    public MarketCacheService<Stock, StockCandle> stockCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            StockRepository stockRepository,
            StockCandleRepository stockCandleRepository) {
        return new MarketCacheService<>(
                redisTemplate, objectMapper,
                "market:stock:snapshot:", "market:stock:history:",
                Duration.ofHours(24), Stock.class,
                new TypeReference<>() {}, "Stock",
                stockRepository::findById,
                stockCandleRepository::findByStockSymbolOrderByCandleDateAsc,
                stockRepository::findAllSymbols
        );
    }

    @Bean
    public MarketCacheService<Forex, ForexCandle> forexCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            ForexRepository forexRepository,
            ForexCandleRepository forexCandleRepository) {
        return new MarketCacheService<>(
                redisTemplate, objectMapper,
                "market:forex:snapshot:", "market:forex:history:",
                Duration.ofHours(24), Forex.class,
                new TypeReference<>() {}, "Forex",
                forexRepository::findById,
                forexCandleRepository::findTop1825ByCurrencyCodeOrderByCandleDateAsc,
                forexRepository::findAllCurrencyCodes
        );
    }

    @Bean
    public MarketCacheService<Fund, FundCandle> fundCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            FundRepository fundRepository,
            FundCandleRepository fundCandleRepository) {
        return new MarketCacheService<>(
                redisTemplate, objectMapper,
                "market:fund:snapshot:", "market:fund:history:",
                Duration.ofHours(24), Fund.class,
                new TypeReference<>() {}, "Fund",
                fundRepository::findById,
                fundCandleRepository::findByFundCodeOrderByCandleDateAsc,
                fundRepository::findAllFundCodes
        );
    }
}
