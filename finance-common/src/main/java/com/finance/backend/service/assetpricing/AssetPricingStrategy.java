package com.finance.backend.service.assetpricing;

import com.finance.backend.model.MarketType;
import com.finance.backend.service.AssetPricingPort;

import java.math.BigDecimal;

public interface AssetPricingStrategy {

    MarketType marketType();

    BigDecimal getPriceTry(String assetCode);

    BigDecimal getSellPriceTry(String assetCode);

    AssetPricingPort.AssetMeta getAssetMeta(String assetCode);

    AssetPricingPort.PriceBundle getBundle(String assetCode);
}
