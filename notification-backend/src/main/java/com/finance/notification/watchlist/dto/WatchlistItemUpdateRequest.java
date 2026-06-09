package com.finance.notification.watchlist.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Partial update for a watchlist item's note and delta threshold; null fields are left unchanged. */
public record WatchlistItemUpdateRequest(
        @Size(max = 50, message = "{validation.watchlist.note.maxLen}") String note,
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
}
