package com.finance.market.core.service.assetpricing;

import com.finance.market.core.model.BaseAsset;
import com.finance.shared.service.AssetPricingPort;

import java.math.BigDecimal;
import java.math.RoundingMode;

public abstract class BaseAssetPricingStrategy implements AssetPricingStrategy {

    protected static final int PRICE_SCALE = 4;
    protected static final AssetPricingPort.AssetMeta EMPTY_META = new AssetPricingPort.AssetMeta(null, null);

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
        return new AssetPricingPort.AssetMeta(asset.resolveDisplayName(), null);
    }
}
