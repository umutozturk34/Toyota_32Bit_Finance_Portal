package com.finance.market.core.service.assetpricing;

import com.finance.common.model.MarketType;
import com.finance.common.service.AssetPricingPort;

import java.math.BigDecimal;

public interface AssetPricingStrategy {

    MarketType marketType();

    BigDecimal getPriceTry(String assetCode);

    BigDecimal getSellPriceTry(String assetCode);

    AssetPricingPort.AssetMeta getAssetMeta(String assetCode);

    AssetPricingPort.PriceBundle getBundle(String assetCode);
}
