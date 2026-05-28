package com.finance.notification.watchlist.dto;

import com.finance.common.model.MarketType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request to add an asset to a watchlist with an optional note and per-item delta threshold; a zero
 * threshold means notify on any move, and bonds are not watchable.
 */
public record WatchlistItemCreateRequest(
        @NotNull(message = "{validation.watchlist.marketType.required}") MarketType marketType,
        @NotBlank(message = "{validation.watchlist.assetCode.required}") @Size(max = 32, message = "{validation.watchlist.assetCode.maxLen}") String assetCode,
        @Size(max = 255, message = "{validation.watchlist.note.maxLen}") String note,
        @DecimalMin(value = "0", inclusive = true, message = "{validation.watchlist.threshold.nonNegative}")
        @DecimalMax(value = "999.9999", message = "{validation.watchlist.threshold.maxPercent}")
        BigDecimal deltaThreshold
) {
    private static final BigDecimal MIN_NON_ZERO = new BigDecimal("0.0001");

    @AssertTrue(message = "{validation.watchlist.threshold.granularity}")
    public boolean isThresholdGranularityValid() {
        if (deltaThreshold == null) return true;
        if (deltaThreshold.signum() == 0) return true;
        return deltaThreshold.compareTo(MIN_NON_ZERO) >= 0;
    }

    @AssertTrue(message = "{validation.watchlist.market.notWatchable}")
    public boolean isMarketTypeWatchable() {
        return marketType != MarketType.BOND;
    }
}
