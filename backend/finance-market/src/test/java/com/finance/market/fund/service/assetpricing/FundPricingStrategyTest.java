package com.finance.market.fund.service.assetpricing;

import com.finance.common.model.MarketType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.shared.service.AssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundPricingStrategyTest {

    private static final String CODE = "TYH";

    @Mock private MarketCacheService<Fund> cacheService;
    @Mock private FundCandleRepository candleRepository;

    private FundPricingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FundPricingStrategy(cacheService, candleRepository);
    }

    @Test
    void marketType_returnsFund() {
        assertThat(strategy.marketType()).isEqualTo(MarketType.FUND);
    }

    @Test
    void getPriceTry_returnsNull_whenNoSnapshot() {
        when(cacheService.getSnapshot(CODE)).thenReturn(null);

        assertThat(strategy.getPriceTry(CODE)).isNull();
    }

    @Test
    void getPriceTry_returnsNormalizedPrice_whenSnapshotExists() {
        Fund fund = Fund.builder().build();
        fund.setPrice(new BigDecimal("1.234567"));
        when(cacheService.getSnapshot(CODE)).thenReturn(fund);

        BigDecimal price = strategy.getPriceTry(CODE);

        assertThat(price).isEqualByComparingTo(new BigDecimal("1.2346"));
    }

    @Test
    void getAssetMeta_returnsEmptyMeta_whenNoSnapshot() {
        when(cacheService.getSnapshot(CODE)).thenReturn(null);

        AssetPricingPort.AssetMeta meta = strategy.getAssetMeta(CODE);

        assertThat(meta.name()).isNull();
        assertThat(meta.image()).isNull();
    }

    @Test
    void getBundle_returnsBundleFromSnapshot() {
        Fund fund = Fund.builder().build();
        fund.setPrice(new BigDecimal("2.50"));
        when(cacheService.getSnapshot(CODE)).thenReturn(fund);

        AssetPricingPort.PriceBundle bundle = strategy.getBundle(CODE);

        assertThat(bundle.price()).isEqualByComparingTo(new BigDecimal("2.5000"));
    }

    @Test
    void getBundle_returnsNullPriceAndEmptyMeta_whenNoSnapshot() {
        when(cacheService.getSnapshot(CODE)).thenReturn(null);

        AssetPricingPort.PriceBundle bundle = strategy.getBundle(CODE);

        assertThat(bundle.price()).isNull();
        assertThat(bundle.meta().name()).isNull();
    }
}
