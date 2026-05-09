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
            return BigDecimal.valueOf(100);
        }

        @Override
        public BigDecimal getSellPriceTry(MarketType type, String assetCode) {
            return BigDecimal.valueOf(99);
        }

        @Override
        public AssetMeta getAssetMeta(MarketType type, String assetCode) {
            return new AssetMeta("NAME-" + assetCode, "IMG-" + assetCode);
        }
    };

    @Test
    void getBundleCombinesPriceSellPriceAndMeta() {
        PriceBundle bundle = port.getBundle(MarketType.CRYPTO, "bitcoin");

        assertThat(bundle.price()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(bundle.sellPrice()).isEqualByComparingTo(BigDecimal.valueOf(99));
        assertThat(bundle.meta().name()).isEqualTo("NAME-bitcoin");
        assertThat(bundle.meta().image()).isEqualTo("IMG-bitcoin");
    }

    @Test
    void getBundlesBuildsMapFromKeys() {
        List<AssetKey> keys = List.of(
                new AssetKey(MarketType.CRYPTO, "bitcoin"),
                new AssetKey(MarketType.STOCK, "THYAO.IS"));

        Map<AssetKey, PriceBundle> bundles = port.getBundles(keys);

        assertThat(bundles).hasSize(2);
        assertThat(bundles.get(new AssetKey(MarketType.CRYPTO, "bitcoin")).meta().name()).isEqualTo("NAME-bitcoin");
        assertThat(bundles.get(new AssetKey(MarketType.STOCK, "THYAO.IS")).meta().name()).isEqualTo("NAME-THYAO.IS");
    }

    @Test
    void getBundlesReturnsEmptyMapForEmptyInput() {
        Map<AssetKey, PriceBundle> bundles = port.getBundles(List.of());
        assertThat(bundles).isEmpty();
    }
}
