package com.finance.market.core.util;

import com.finance.market.core.model.BaseAsset;

import java.math.BigDecimal;

/**
 * Fills in an asset's day change from its candle history only when the source did not already
 * provide one, so source-supplied change values are never overwritten.
 */
public final class ChangeFromCandlesUpdater {

    private ChangeFromCandlesUpdater() {}

    /**
     * Computes change from current price vs. an explicit prior-day close, but only when the asset's
     * change percent is currently absent/zero. The caller is expected to have resolved the prior
     * close as "the most recent close strictly before today" — typically via a
     * {@code findFirstBy...AndCandleDateBeforeOrderByCandleDateDesc} query — so that an intraday
     * row for today (if any) is excluded.
     */
    public static boolean applyFromPriorCloseIfMissing(BaseAsset asset,
                                                       BigDecimal currentPrice,
                                                       BigDecimal priorClose,
                                                       int scale) {
        BigDecimal existing = asset.getChangePercent();
        if (existing != null && existing.signum() != 0) return false;
        if (currentPrice == null || priorClose == null) return false;
        asset.applyChange(currentPrice, priorClose, scale);
        return true;
    }
}
