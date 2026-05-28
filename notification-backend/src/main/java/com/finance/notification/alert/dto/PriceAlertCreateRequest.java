package com.finance.notification.alert.dto;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request to create a price alert. The threshold may be supplied in any currency (defaulting to TRY)
 * and is converted server-side; bond markets are not alertable and percent thresholds are capped.
 */
public record PriceAlertCreateRequest(
        @NotNull(message = "{validation.alert.marketType.required}") MarketType marketType,
        @NotBlank(message = "{validation.alert.assetCode.required}") @Size(max = 32, message = "{validation.alert.assetCode.maxLen}") String assetCode,
        @NotNull(message = "{validation.alert.direction.required}") AlertDirection direction,
        @NotNull(message = "{validation.alert.threshold.required}")
        @DecimalMin(value = "0", inclusive = false, message = "{validation.alert.threshold.positive}")
        @DecimalMax(value = "999999999999.9999", message = "{validation.alert.threshold.tooLarge}")
        BigDecimal threshold,
        @Size(max = 8) String currency,
        BigDecimal referencePrice
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

    @AssertTrue(message = "{validation.alert.market.notAlertable}")
    public boolean isMarketTypeAlertable() {
        return marketType != MarketType.BOND;
    }
}
