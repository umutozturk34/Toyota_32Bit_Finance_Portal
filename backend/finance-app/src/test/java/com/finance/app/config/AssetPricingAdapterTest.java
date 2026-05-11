package com.finance.app.config;

import com.finance.market.stock.model.Stock;
import com.finance.market.fund.model.Fund;
import com.finance.market.forex.model.Forex;
import com.finance.market.crypto.model.Crypto;
import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort.AssetMeta;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.crypto.service.assetpricing.CryptoPricingStrategy;
import com.finance.market.forex.service.assetpricing.ForexPricingStrategy;
import com.finance.market.fund.service.assetpricing.FundPricingStrategy;
import com.finance.market.stock.service.assetpricing.StockPricingStrategy;
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

    @Mock private MarketCacheService<Crypto> cryptoCacheService;
    @Mock private MarketCacheService<Stock> stockCacheService;
    @Mock private MarketCacheService<Forex> forexCacheService;
    @Mock private MarketCacheService<Fund> fundCacheService;

    private AssetPricingAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AssetPricingAdapter(List.of(
            new CryptoPricingStrategy(cryptoCacheService),
            new StockPricingStrategy(stockCacheService),
            new ForexPricingStrategy(forexCacheService),
            new FundPricingStrategy(fundCacheService)
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
