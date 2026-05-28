package com.finance.market.core.util;

import com.finance.market.core.model.BaseAsset;
import com.finance.market.core.model.BaseCandle;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fills in an asset's day change from its candle history only when the source did not already
 * provide one, so source-supplied change values are never overwritten.
 */
public final class ChangeFromCandlesUpdater {

    private ChangeFromCandlesUpdater() {}

    /**
     * Computes change from current price vs. the previous candle's close, but only when the asset's
     * change percent is currently absent/zero. Returns whether a value was applied.
     */
    public static boolean applyFromTopTwoDescIfMissing(BaseAsset asset,
                                                       BigDecimal currentPrice,
                                                       List<? extends BaseCandle> topTwoDesc,
                                                       int scale) {
        BigDecimal existing = asset.getChangePercent();
        if (existing != null && existing.signum() != 0) return false;
        if (currentPrice == null || topTwoDesc.size() < 2) return false;
        BigDecimal previousClose = topTwoDesc.get(1).getClose();
        if (previousClose == null) return false;
        asset.applyChange(currentPrice, previousClose, scale);
        return true;
    }
}
