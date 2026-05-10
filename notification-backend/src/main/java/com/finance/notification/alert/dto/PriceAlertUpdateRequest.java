package com.finance.notification.alert.dto;

import com.finance.notification.alert.model.AlertDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record PriceAlertUpdateRequest(
        AlertDirection direction,
        @DecimalMin(value = "0", inclusive = false, message = "{validation.alert.threshold.positive}")
        @DecimalMax(value = "999999999999.9999", message = "{validation.alert.threshold.tooLarge}")
        BigDecimal threshold
) {
    private static final BigDecimal MIN_GRANULARITY = new BigDecimal("0.0001");
    private static final BigDecimal MAX_PCT = new BigDecimal("999.9999");

    @AssertTrue(message = "{validation.alert.threshold.granularity}")
    public boolean isThresholdGranularityValid() {
        if (threshold == null) return true;
        return threshold.compareTo(MIN_GRANULARITY) >= 0;
    }

    @AssertTrue(message = "{validation.alert.threshold.maxPercent}")
    public boolean isPercentDirectionWithinRange() {
        if (threshold == null || direction == null) return true;
        boolean pct = direction == AlertDirection.CHANGE_PCT_UP || direction == AlertDirection.CHANGE_PCT_DOWN;
        if (!pct) return true;
        return threshold.compareTo(MAX_PCT) <= 0;
    }
}
