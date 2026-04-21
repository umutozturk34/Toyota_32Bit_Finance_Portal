package com.finance.backend.service.assetpricing;

import com.finance.backend.config.AppProperties;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.service.AssetPricingPort;
import com.finance.backend.service.MarketCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommodityPricingStrategyTest {

    @SuppressWarnings("unchecked")
    private final MarketCacheService<Commodity, CommodityCandle> cacheService = mock(MarketCacheService.class);
    private CommodityPricingStrategy strategy;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        AppProperties.Commission commission = new AppProperties.Commission();
        commission.setCommodityRate(new BigDecimal("0.0025"));
        props.setCommission(commission);

        strategy = new CommodityPricingStrategy(cacheService, props);
    }

    @Test
    void marketTypeReturnsCommodity() {
        assertThat(strategy.marketType()).isEqualTo(MarketType.COMMODITY);
    }

    @Test
    void getPriceTryReturnsNormalizedCurrentPrice() {
        when(cacheService.getSnapshot("GC=F")).thenReturn(buildCommodity(new BigDecimal("160000.1234567")));

        BigDecimal price = strategy.getPriceTry("GC=F");

        assertThat(price).isEqualByComparingTo("160000.1235");
    }

    @Test
    void getPriceTryReturnsNullWhenSnapshotMissing() {
        when(cacheService.getSnapshot("UNKNOWN")).thenReturn(null);

        assertThat(strategy.getPriceTry("UNKNOWN")).isNull();
    }

    @Test
    void getSellPriceTryAppliesCommissionRate() {
        when(cacheService.getSnapshot("GC=F")).thenReturn(buildCommodity(new BigDecimal("160000.0000")));

        BigDecimal sellPrice = strategy.getSellPriceTry("GC=F");

        assertThat(sellPrice).isEqualByComparingTo("159600.0000");
    }

    @Test
    void getAssetMetaReturnsDisplayNameFromCommodity() {
        Commodity commodity = buildCommodity(new BigDecimal("100"));
        commodity.setName("Altın");
        when(cacheService.getSnapshot("GC=F")).thenReturn(commodity);

        AssetPricingPort.AssetMeta meta = strategy.getAssetMeta("GC=F");

        assertThat(meta.name()).isEqualTo("Altın");
    }

    @Test
    void getBundleReturnsEmptyMetaForMissingSnapshot() {
        when(cacheService.getSnapshot("MISSING")).thenReturn(null);

        AssetPricingPort.PriceBundle bundle = strategy.getBundle("MISSING");

        assertThat(bundle.price()).isNull();
        assertThat(bundle.sellPrice()).isNull();
        assertThat(bundle.meta().name()).isNull();
        assertThat(bundle.meta().image()).isNull();
    }

    @Test
    void getBundleBuildsCompleteBundleWithImage() {
        Commodity commodity = buildCommodity(new BigDecimal("160000.0000"));
        commodity.setName("Altın");
        commodity.setImage("/img/gold.svg");
        when(cacheService.getSnapshot("GC=F")).thenReturn(commodity);

        AssetPricingPort.PriceBundle bundle = strategy.getBundle("GC=F");

        assertThat(bundle.price()).isEqualByComparingTo("160000.0000");
        assertThat(bundle.sellPrice()).isEqualByComparingTo("159600.0000");
        assertThat(bundle.meta().name()).isEqualTo("Altın");
        assertThat(bundle.meta().image()).isEqualTo("/img/gold.svg");
    }

    private Commodity buildCommodity(BigDecimal currentPrice) {
        Commodity commodity = new Commodity();
        commodity.setCommodityCode("GC=F");
        commodity.setCurrentPrice(currentPrice);
        return commodity;
    }
}
