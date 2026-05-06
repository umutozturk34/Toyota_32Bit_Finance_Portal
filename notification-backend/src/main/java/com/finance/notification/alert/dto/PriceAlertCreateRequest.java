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

public record PriceAlertCreateRequest(
        @NotNull(message = "Piyasa tipi zorunlu") MarketType marketType,
        @NotBlank(message = "Varlık kodu zorunlu") @Size(max = 32, message = "Varlık kodu en fazla 32 karakter olabilir") String assetCode,
        @NotNull(message = "Yön zorunlu") AlertDirection direction,
        @NotNull(message = "Eşik zorunlu")
        @DecimalMin(value = "0", inclusive = false, message = "Eşik 0'dan büyük olmalı")
        @DecimalMax(value = "999999999999.9999", message = "Eşik çok büyük")
        BigDecimal threshold,
        @Size(max = 8) String currency,
        BigDecimal referencePrice
) {
    private static final BigDecimal MIN_GRANULARITY = new BigDecimal("0.0001");
    private static final BigDecimal MAX_PCT = new BigDecimal("999.9999");

    @AssertTrue(message = "Eşik en az 0.0001 olmalı (DB hassasiyeti)")
    public boolean isThresholdGranularityValid() {
        if (threshold == null) return true;
        return threshold.compareTo(MIN_GRANULARITY) >= 0;
    }

    @AssertTrue(message = "Yüzdesel yönlerde eşik en fazla 999.9999% olabilir")
    public boolean isPercentDirectionWithinRange() {
        if (threshold == null || direction == null) return true;
        boolean pct = direction == AlertDirection.CHANGE_PCT_UP || direction == AlertDirection.CHANGE_PCT_DOWN;
        if (!pct) return true;
        return threshold.compareTo(MAX_PCT) <= 0;
    }
}
