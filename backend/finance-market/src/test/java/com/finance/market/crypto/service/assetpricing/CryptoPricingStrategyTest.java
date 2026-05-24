package com.finance.market.crypto.service.assetpricing;

import com.finance.common.model.MarketType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.crypto.model.Crypto;
import com.finance.market.crypto.repository.CryptoCandleRepository;
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
class CryptoPricingStrategyTest {

    private static final String CODE = "bitcoin";

    @Mock private MarketCacheService<Crypto> cacheService;
    @Mock private CryptoCandleRepository candleRepository;

    private CryptoPricingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new CryptoPricingStrategy(cacheService, candleRepository);
    }

    @Test
    void marketType_returnsCrypto() {
        assertThat(strategy.marketType()).isEqualTo(MarketType.CRYPTO);
    }

    @Test
    void getPriceTry_returnsNull_whenNoSnapshot() {
        when(cacheService.getSnapshot(CODE)).thenReturn(null);

        BigDecimal price = strategy.getPriceTry(CODE);

        assertThat(price).isNull();
    }

    @Test
    void getPriceTry_returnsNormalizedTryPrice_whenSnapshotExists() {
        Crypto crypto = Crypto.builder().build();
        crypto.setCurrentPriceTry(new BigDecimal("3500"));
        when(cacheService.getSnapshot(CODE)).thenReturn(crypto);

        BigDecimal price = strategy.getPriceTry(CODE);

        assertThat(price).isEqualByComparingTo(new BigDecimal("3500.0000"));
    }

    @Test
    void getAssetMeta_returnsEmptyMeta_whenNoSnapshot() {
        when(cacheService.getSnapshot(CODE)).thenReturn(null);

        AssetPricingPort.AssetMeta meta = strategy.getAssetMeta(CODE);

        assertThat(meta.name()).isNull();
        assertThat(meta.image()).isNull();
    }

    @Test
    void getAssetMeta_returnsDisplayNameAndImage_whenSnapshotExists() {
        Crypto crypto = Crypto.builder().symbol("BTC").image("https://x/btc.png").build();
        when(cacheService.getSnapshot(CODE)).thenReturn(crypto);

        AssetPricingPort.AssetMeta meta = strategy.getAssetMeta(CODE);

        assertThat(meta.name()).isEqualTo("BTC");
        assertThat(meta.image()).isEqualTo("https://x/btc.png");
    }

    @Test
    void getBundle_returnsNullPriceAndEmptyMeta_whenNoSnapshot() {
        when(cacheService.getSnapshot(CODE)).thenReturn(null);

        AssetPricingPort.PriceBundle bundle = strategy.getBundle(CODE);

        assertThat(bundle.price()).isNull();
        assertThat(bundle.meta().name()).isNull();
    }

    @Test
    void getBundle_returnsPriceAndMeta_whenSnapshotExists() {
        Crypto crypto = Crypto.builder().symbol("BTC").image("img").build();
        crypto.setCurrentPriceTry(new BigDecimal("1234.56"));
        when(cacheService.getSnapshot(CODE)).thenReturn(crypto);

        AssetPricingPort.PriceBundle bundle = strategy.getBundle(CODE);

        assertThat(bundle.price()).isEqualByComparingTo(new BigDecimal("1234.5600"));
        assertThat(bundle.meta().name()).isEqualTo("BTC");
        assertThat(bundle.meta().image()).isEqualTo("img");
    }
}
