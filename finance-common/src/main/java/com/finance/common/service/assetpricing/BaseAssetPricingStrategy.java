package com.finance.common.service.assetpricing;

import com.finance.common.model.BaseAsset;
import com.finance.common.service.AssetPricingPort;

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

    protected BigDecimal applyCommission(BigDecimal price, BigDecimal rate) {
        if (price == null) {
            return null;
        }
        return normalize(price.multiply(BigDecimal.ONE.subtract(rate)));
    }

    protected AssetPricingPort.AssetMeta baseMeta(BaseAsset asset) {
        if (asset == null) {
            return EMPTY_META;
        }
        return new AssetPricingPort.AssetMeta(asset.resolveDisplayName(), null);
    }
}
