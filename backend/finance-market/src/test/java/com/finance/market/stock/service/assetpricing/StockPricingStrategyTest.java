package com.finance.market.stock.service.assetpricing;

import com.finance.common.model.MarketType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockCandle;
import com.finance.market.stock.repository.StockCandleRepository;
import com.finance.shared.service.AssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockPricingStrategyTest {

    private static final String CODE = "AKBNK.IS";

    @Mock private MarketCacheService<Stock> cacheService;
    @Mock private StockCandleRepository candleRepository;

    private StockPricingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new StockPricingStrategy(cacheService, candleRepository);
        lenient().when(candleRepository.findFirstByStockSymbolAndCloseGreaterThanOrderByCandleDateDesc(CODE, BigDecimal.ZERO))
                .thenReturn(Optional.empty());
    }

    @Test
    void getPriceTry_fallsBackToLastCandleClose_whenCurrentPriceMissing() {
        Stock stock = Stock.builder().build();
        when(cacheService.getSnapshot(CODE)).thenReturn(stock);
        StockCandle candle = new StockCandle();
        candle.setClose(new BigDecimal("12.7"));
        when(candleRepository.findFirstByStockSymbolAndCloseGreaterThanOrderByCandleDateDesc(CODE, BigDecimal.ZERO))
                .thenReturn(Optional.of(candle));

        BigDecimal price = strategy.getPriceTry(CODE);

        assertThat(price).isEqualByComparingTo(new BigDecimal("12.7000"));
    }

    @Test
    void marketType_returnsStock() {
        assertThat(strategy.marketType()).isEqualTo(MarketType.STOCK);
    }

    @Test
    void getPriceTry_returnsNull_whenNoSnapshot() {
        when(cacheService.getSnapshot(CODE)).thenReturn(null);

        assertThat(strategy.getPriceTry(CODE)).isNull();
    }

    @Test
    void getPriceTry_returnsNormalizedCurrentPrice() {
        Stock stock = Stock.builder().build();
        stock.setCurrentPrice(new BigDecimal("42.5"));
        when(cacheService.getSnapshot(CODE)).thenReturn(stock);

        BigDecimal price = strategy.getPriceTry(CODE);

        assertThat(price).isEqualByComparingTo(new BigDecimal("42.5000"));
    }

    @Test
    void getAssetMeta_returnsEmptyMeta_whenNoSnapshot() {
        when(cacheService.getSnapshot(CODE)).thenReturn(null);

        AssetPricingPort.AssetMeta meta = strategy.getAssetMeta(CODE);

        assertThat(meta.name()).isNull();
        assertThat(meta.image()).isNull();
    }

    @Test
    void getBundle_returnsBundle_whenSnapshotExists() {
        Stock stock = Stock.builder().build();
        stock.setCurrentPrice(new BigDecimal("100"));
        when(cacheService.getSnapshot(CODE)).thenReturn(stock);

        AssetPricingPort.PriceBundle bundle = strategy.getBundle(CODE);

        assertThat(bundle.price()).isEqualByComparingTo(new BigDecimal("100.0000"));
    }

    @Test
    void getBundle_returnsNullPriceAndEmptyMeta_whenNoSnapshot() {
        when(cacheService.getSnapshot(CODE)).thenReturn(null);

        AssetPricingPort.PriceBundle bundle = strategy.getBundle(CODE);

        assertThat(bundle.price()).isNull();
        assertThat(bundle.meta().name()).isNull();
    }
}
