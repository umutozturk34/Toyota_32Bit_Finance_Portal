package com.finance.market.core.service.assetpricing;

import com.finance.common.model.MarketType;
import com.finance.shared.service.AssetPricingPort;

import java.math.BigDecimal;

/**
 * Per-market valuation rule that converts an asset's native price to TRY for portfolio pricing.
 * One implementation owns one {@link MarketType}; the registry dispatches by {@link #marketType()}.
 */
public interface AssetPricingStrategy {

    /** Market this strategy prices. */
    MarketType marketType();

    /** Current price of the asset in TRY, or {@code null} when no price is available. */
    BigDecimal getPriceTry(String assetCode);

    /**
     * TRY price used when valuing a position's exit/close; defaults to the live price but a market
     * may override (e.g. to apply a different settlement convention).
     */
    default BigDecimal getExitPriceTry(String assetCode) {
        return getPriceTry(assetCode);
    }

    AssetPricingPort.AssetMeta getAssetMeta(String assetCode);

    /** Price plus display metadata in one lookup, avoiding a second round-trip. */
    AssetPricingPort.PriceBundle getBundle(String assetCode);
}
