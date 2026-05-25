package com.finance.market.forex.service.assetpricing;

import com.finance.common.model.MarketType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.shared.service.AssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForexPricingStrategyTest {

    @SuppressWarnings("unchecked")
    private final MarketCacheService<Forex> cacheService = mock(MarketCacheService.class);
    private final ForexCandleRepository candleRepository = mock(ForexCandleRepository.class);

    private ForexPricingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ForexPricingStrategy(cacheService, candleRepository);
    }

    @Test
    void should_returnForexType_when_marketTypeCalled() {
        assertThat(strategy.marketType()).isEqualTo(MarketType.FOREX);
    }

    @Test
    void should_returnSellingPrice_when_getPriceTryWithCachedForex() {
        Forex usd = Forex.builder().currencyCode("USD")
                .sellingPrice(new BigDecimal("45.2714")).build();
        when(cacheService.getSnapshot("USD")).thenReturn(usd);

        BigDecimal price = strategy.getPriceTry("USD");

        assertThat(price).isEqualByComparingTo("45.2714");
    }

    @Test
    void should_returnNull_when_cacheMisses() {
        when(cacheService.getSnapshot("USD")).thenReturn(null);

        BigDecimal price = strategy.getPriceTry("USD");

        assertThat(price).isNull();
    }

    @Test
    void should_returnPriceBundle_when_getBundleWithCachedForex() {
        Forex usd = Forex.builder().currencyCode("USD")
                .sellingPrice(new BigDecimal("45.2714"))
                .image("🇺🇸")
                .build();
        when(cacheService.getSnapshot("USD")).thenReturn(usd);

        AssetPricingPort.PriceBundle bundle = strategy.getBundle("USD");

        assertThat(bundle.price()).isEqualByComparingTo("45.2714");
        assertThat(bundle.meta().name()).isEqualTo("USD");
        assertThat(bundle.meta().image()).isEqualTo("🇺🇸");
    }

    @Test
    void should_returnEmptyBundle_when_cacheMissesForBundle() {
        when(cacheService.getSnapshot("XYZ")).thenReturn(null);

        AssetPricingPort.PriceBundle bundle = strategy.getBundle("XYZ");

        assertThat(bundle.price()).isNull();
        assertThat(bundle.meta()).isNotNull();
    }

    @Test
    void should_returnBuyingPrice_when_getExitPriceTryWithBuyingPriceSet() {
        Forex usd = Forex.builder().currencyCode("USD")
                .buyingPrice(new BigDecimal("45.0000"))
                .sellingPrice(new BigDecimal("45.2714"))
                .build();
        when(cacheService.getSnapshot("USD")).thenReturn(usd);

        BigDecimal exit = strategy.getExitPriceTry("USD");

        assertThat(exit).isEqualByComparingTo("45.0000");
    }

    @Test
    void should_returnSellingPrice_when_getExitPriceTryAndBuyingMissing() {
        Forex usd = Forex.builder().currencyCode("USD")
                .sellingPrice(new BigDecimal("45.2714"))
                .build();
        when(cacheService.getSnapshot("USD")).thenReturn(usd);

        BigDecimal exit = strategy.getExitPriceTry("USD");

        assertThat(exit).isEqualByComparingTo("45.2714");
    }

    @Test
    void should_returnNull_when_getExitPriceTryAndCacheMisses() {
        when(cacheService.getSnapshot("XYZ")).thenReturn(null);

        BigDecimal exit = strategy.getExitPriceTry("XYZ");

        assertThat(exit).isNull();
    }

    @Test
    void should_returnAssetMeta_when_getAssetMetaWithCachedForex() {
        Forex usd = Forex.builder().currencyCode("USD")
                .sellingPrice(new BigDecimal("45.2714"))
                .image("flag")
                .build();
        when(cacheService.getSnapshot("USD")).thenReturn(usd);

        AssetPricingPort.AssetMeta meta = strategy.getAssetMeta("USD");

        assertThat(meta).isNotNull();
    }
}
