package com.finance.market.core.service.assetpricing;

import com.finance.market.core.model.BaseAsset;
import com.finance.shared.service.AssetPricingPort;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared base for {@link AssetPricingStrategy} implementations: normalizes prices to a common
 * scale and builds display metadata from a {@link BaseAsset}.
 */
public abstract class BaseAssetPricingStrategy implements AssetPricingStrategy {

    protected static final int PRICE_SCALE = 4;
    protected static final AssetPricingPort.AssetMeta EMPTY_META = new AssetPricingPort.AssetMeta(null, null);

    /** Rounds a price to {@link #PRICE_SCALE} (HALF_UP), preserving {@code null}. */
    protected BigDecimal normalize(BigDecimal price) {
        if (price == null) {
            return null;
        }
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    protected AssetPricingPort.AssetMeta baseMeta(BaseAsset asset) {
        if (asset == null) {
            return EMPTY_META;
        }
        return new AssetPricingPort.AssetMeta(asset.resolveDisplayName(), asset.getImage());
    }
}
