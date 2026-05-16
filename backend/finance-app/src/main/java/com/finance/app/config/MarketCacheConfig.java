package com.finance.app.config;

import tools.jackson.databind.ObjectMapper;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.repository.CommodityRepository;
import com.finance.common.config.AppProperties;
import com.finance.shared.util.RedisKeys;
import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.repository.CryptoRepository;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.repository.ForexRepository;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.repository.FundRepository;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.repository.StockRepository;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.repository.ViopContractRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

@Configuration
public class MarketCacheConfig {

    private final Duration ttl;

    public MarketCacheConfig(AppProperties appProperties) {
        this.ttl = Duration.ofHours(appProperties.getCache().getRedisDefaultTtlHours());
    }

    @Bean
    public MarketCacheService<Crypto> cryptoCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            CryptoRepository cryptoRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                RedisKeys.marketSnapshotPrefix("Crypto"), ttl, Crypto.class, "Crypto",
                cryptoRepository::findById);
    }

    @Bean
    public MarketCacheService<Stock> stockCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            StockRepository stockRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                RedisKeys.marketSnapshotPrefix("Stock"), ttl, Stock.class, "Stock",
                stockRepository::findById);
    }

    @Bean
    public MarketCacheService<Forex> forexCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            ForexRepository forexRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                RedisKeys.marketSnapshotPrefix("Forex"), ttl, Forex.class, "Forex",
                forexRepository::findById);
    }

    @Bean
    public MarketCacheService<Fund> fundCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            FundRepository fundRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                RedisKeys.marketSnapshotPrefix("Fund"), ttl, Fund.class, "Fund",
                fundRepository::findById);
    }

    @Bean
    public MarketCacheService<Commodity> commodityCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            CommodityRepository commodityRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                RedisKeys.marketSnapshotPrefix("Commodity"), ttl, Commodity.class, "Commodity",
                commodityRepository::findById);
    }

    @Bean
    public MarketCacheService<Bond> bondCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            BondRepository bondRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                RedisKeys.marketSnapshotPrefix("Bond"), ttl, Bond.class, "Bond",
                bondRepository::findById);
    }

    @Bean
    public MarketCacheService<ViopContract> viopCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            ViopContractRepository viopContractRepository) {
        return new MarketCacheService<>(redisTemplate, objectMapper,
                RedisKeys.marketSnapshotPrefix("Viop"), ttl, ViopContract.class, "Viop",
                viopContractRepository::findBySymbol);
    }
}
