package com.finance.shared.service;

import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.shared.service.AssetPricingPort.PriceBundle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssetPricingPortTest {

    private final AssetPricingPort port = new AssetPricingPort() {
        @Override
        public BigDecimal getPriceTry(MarketType type, String assetCode) {
            if ("MISSING".equals(assetCode)) return null;
            return BigDecimal.valueOf(100);
        }

        @Override
        public AssetMeta getAssetMeta(MarketType type, String assetCode) {
            return new AssetMeta("NAME-" + assetCode, "IMG-" + assetCode);
        }
    };

    private final AssetPricingPort exitDifferingPort = new AssetPricingPort() {
        @Override
        public BigDecimal getPriceTry(MarketType type, String assetCode) {
            return BigDecimal.valueOf(100);
        }

        @Override
        public BigDecimal getExitPriceTry(MarketType type, String assetCode) {
            return BigDecimal.valueOf(95);
        }
    };

    @Test
    void getBundle_combinesPriceAndMeta() {
        PriceBundle bundle = port.getBundle(MarketType.CRYPTO, "bitcoin");

        assertThat(bundle.price()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(bundle.meta().name()).isEqualTo("NAME-bitcoin");
        assertThat(bundle.meta().image()).isEqualTo("IMG-bitcoin");
    }

    @Test
    void defaultExitPriceTry_fallsBackToGetPriceTry() {
        BigDecimal price = port.getExitPriceTry(MarketType.STOCK, "AAPL");

        assertThat(price).isEqualByComparingTo("100");
    }

    @Test
    void defaultGetAssetMeta_returnsNullNameAndImage() {
        AssetPricingPort baseline = (t, c) -> BigDecimal.ONE;

        AssetPricingPort.AssetMeta meta = baseline.getAssetMeta(MarketType.STOCK, "X");

        assertThat(meta.name()).isNull();
        assertThat(meta.image()).isNull();
    }

    @Test
    void getExitBundle_usesExitPrice_whenOverridden() {
        PriceBundle bundle = exitDifferingPort.getExitBundle(MarketType.STOCK, "AAPL");

        assertThat(bundle.price()).isEqualByComparingTo("95");
    }

    @Test
    void getExitBundles_buildsMapFromKeys() {
        AssetKey k = new AssetKey(MarketType.STOCK, "AAPL");

        Map<AssetKey, PriceBundle> result = exitDifferingPort.getExitBundles(List.of(k));

        assertThat(result.get(k).price()).isEqualByComparingTo("95");
    }

    @Test
    void getExitPricesTry_skipsKeysWithNullPrice() {
        AssetKey k1 = new AssetKey(MarketType.STOCK, "AAPL");
        AssetKey k2 = new AssetKey(MarketType.STOCK, "MISSING");

        Map<AssetKey, BigDecimal> result = port.getExitPricesTry(List.of(k1, k2));

        assertThat(result).containsKey(k1).doesNotContainKey(k2);
    }
}
