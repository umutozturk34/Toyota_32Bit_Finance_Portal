package com.finance.backend.config;

import com.finance.backend.model.*;
import com.finance.backend.service.AssetPricingPort.AssetMeta;
import com.finance.backend.service.MarketCacheService;
import com.finance.backend.service.assetpricing.CryptoPricingStrategy;
import com.finance.backend.service.assetpricing.ForexPricingStrategy;
import com.finance.backend.service.assetpricing.FundPricingStrategy;
import com.finance.backend.service.assetpricing.StockPricingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetPricingAdapterTest {

    @Mock private MarketCacheService<Crypto, CryptoCandle> cryptoCacheService;
    @Mock private MarketCacheService<Stock, StockCandle> stockCacheService;
    @Mock private MarketCacheService<Forex, ForexCandle> forexCacheService;
    @Mock private MarketCacheService<Fund, FundCandle> fundCacheService;

    private AssetPricingAdapter adapter;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        AppProperties.Commission commission = new AppProperties.Commission();
        commission.setStockRate(new BigDecimal("0.002"));
        commission.setCryptoRate(new BigDecimal("0.0015"));
        commission.setFundRate(new BigDecimal("0.001"));
        props.setCommission(commission);

        adapter = new AssetPricingAdapter(List.of(
            new CryptoPricingStrategy(cryptoCacheService, props),
            new StockPricingStrategy(stockCacheService, props),
            new ForexPricingStrategy(forexCacheService),
            new FundPricingStrategy(fundCacheService, props)
        ));
    }

    private Crypto cryptoWith(String priceTry) {
        Crypto c = new Crypto();
        c.setCurrentPriceTry(new BigDecimal(priceTry));
        return c;
    }

    private Stock stockWith(String currentPrice) {
        Stock s = new Stock();
        s.setCurrentPrice(new BigDecimal(currentPrice));
        return s;
    }

    private Fund fundWith(String price) {
        Fund f = new Fund();
        f.setPrice(new BigDecimal(price));
        return f;
    }

    private Forex forexWith(String currentPrice, String sellingPrice) {
        Forex fx = new Forex();
        fx.setCurrentPrice(currentPrice != null ? new BigDecimal(currentPrice) : null);
        fx.setSellingPrice(sellingPrice != null ? new BigDecimal(sellingPrice) : null);
        return fx;
    }

    @Test
    void getPriceTryCryptoReturnsNormalizedPrice() {
        when(cryptoCacheService.getSnapshot("bitcoin")).thenReturn(cryptoWith("65432.123456"));

        BigDecimal price = adapter.getPriceTry(MarketType.CRYPTO, "bitcoin");

        assertThat(price).isEqualByComparingTo(new BigDecimal("65432.1235"));
        assertThat(price.scale()).isEqualTo(4);
    }

    @Test
    void getPriceTryStockReturnsNormalizedPrice() {
        when(stockCacheService.getSnapshot("THYAO.IS")).thenReturn(stockWith("45.678"));

        BigDecimal price = adapter.getPriceTry(MarketType.STOCK, "THYAO.IS");

        assertThat(price).isEqualByComparingTo(new BigDecimal("45.6780"));
    }

    @Test
    void getPriceTryForexUsesSellingPriceWhenAvailable() {
        when(forexCacheService.getSnapshot("USD")).thenReturn(forexWith("38.0000", "38.5000"));

        BigDecimal price = adapter.getPriceTry(MarketType.FOREX, "USD");

        assertThat(price).isEqualByComparingTo(new BigDecimal("38.5000"));
    }

    @Test
    void getPriceTryForexFallsBackToCurrentPriceWhenSellingPriceNull() {
        when(forexCacheService.getSnapshot("USD")).thenReturn(forexWith("38.0000", null));

        BigDecimal price = adapter.getPriceTry(MarketType.FOREX, "USD");

        assertThat(price).isEqualByComparingTo(new BigDecimal("38.0000"));
    }

    @Test
    void getPriceTryFundReturnsNormalizedPrice() {
        when(fundCacheService.getSnapshot("AAK")).thenReturn(fundWith("1.234567"));

        BigDecimal price = adapter.getPriceTry(MarketType.FUND, "AAK");

        assertThat(price).isEqualByComparingTo(new BigDecimal("1.2346"));
    }

    @Test
    void getPriceTryNullSnapshotReturnsNull() {
        when(cryptoCacheService.getSnapshot("unknown")).thenReturn(null);

        assertThat(adapter.getPriceTry(MarketType.CRYPTO, "unknown")).isNull();
    }

    @Test
    void getSellPriceTryCryptoAppliesCommission() {
        when(cryptoCacheService.getSnapshot("bitcoin")).thenReturn(cryptoWith("100000.0000"));

        BigDecimal sellPrice = adapter.getSellPriceTry(MarketType.CRYPTO, "bitcoin");

        assertThat(sellPrice).isEqualByComparingTo(new BigDecimal("99850.0000"));
    }

    @Test
    void getSellPriceTryStockAppliesCommission() {
        when(stockCacheService.getSnapshot("THYAO.IS")).thenReturn(stockWith("50.0000"));

        BigDecimal sellPrice = adapter.getSellPriceTry(MarketType.STOCK, "THYAO.IS");

        assertThat(sellPrice).isEqualByComparingTo(new BigDecimal("49.9000"));
    }

    @Test
    void getSellPriceTryFundAppliesCommission() {
        when(fundCacheService.getSnapshot("BBK")).thenReturn(fundWith("200.0000"));

        BigDecimal sellPrice = adapter.getSellPriceTry(MarketType.FUND, "BBK");

        assertThat(sellPrice).isEqualByComparingTo(new BigDecimal("199.8000"));
    }

    @Test
    void getSellPriceTryForexUsesCurrentPriceNotSellingPrice() {
        when(forexCacheService.getSnapshot("USD")).thenReturn(forexWith("37.5000", "38.5000"));

        BigDecimal sellPrice = adapter.getSellPriceTry(MarketType.FOREX, "USD");

        assertThat(sellPrice).isEqualByComparingTo(new BigDecimal("37.5000"));
    }

    @Test
    void getSellPriceTryNullSnapshotReturnsNull() {
        when(stockCacheService.getSnapshot("DELISTED")).thenReturn(null);

        assertThat(adapter.getSellPriceTry(MarketType.STOCK, "DELISTED")).isNull();
    }

    @Test
    void getAssetMetaCryptoReturnsNameAndImage() {
        Crypto crypto = new Crypto();
        crypto.setName("Bitcoin");
        crypto.setImage("https://example.com/btc.png");
        when(cryptoCacheService.getSnapshot("bitcoin")).thenReturn(crypto);

        AssetMeta meta = adapter.getAssetMeta(MarketType.CRYPTO, "bitcoin");

        assertThat(meta.name()).isEqualTo("Bitcoin");
        assertThat(meta.image()).isEqualTo("https://example.com/btc.png");
    }

    @Test
    void getAssetMetaStockReturnsNameAndNullImage() {
        Stock stock = new Stock();
        stock.setName("Türk Hava Yolları");
        when(stockCacheService.getSnapshot("THYAO.IS")).thenReturn(stock);

        AssetMeta meta = adapter.getAssetMeta(MarketType.STOCK, "THYAO.IS");

        assertThat(meta.name()).isEqualTo("Türk Hava Yolları");
        assertThat(meta.image()).isNull();
    }

    @Test
    void getAssetMetaNullSnapshotReturnsEmptyMeta() {
        when(forexCacheService.getSnapshot("UNKNOWN")).thenReturn(null);

        AssetMeta meta = adapter.getAssetMeta(MarketType.FOREX, "UNKNOWN");

        assertThat(meta.name()).isNull();
        assertThat(meta.image()).isNull();
    }
}
