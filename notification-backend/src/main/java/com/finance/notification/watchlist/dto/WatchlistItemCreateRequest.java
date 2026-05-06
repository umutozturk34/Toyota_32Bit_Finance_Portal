package com.finance.notification.watchlist.dto;

import com.finance.common.model.MarketType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WatchlistItemCreateRequest(
        @NotNull(message = "Piyasa tipi zorunlu") MarketType marketType,
        @NotBlank(message = "Varlık kodu zorunlu") @Size(max = 32, message = "Varlık kodu en fazla 32 karakter olabilir") String assetCode,
        @Size(max = 255, message = "Not en fazla 255 karakter olabilir") String note,
        @DecimalMin(value = "0", inclusive = true, message = "Eşik 0 veya pozitif olmalı")
        @DecimalMax(value = "999.9999", message = "Eşik en fazla 999.9999% olabilir")
        BigDecimal deltaThreshold
) {
    private static final BigDecimal MIN_NON_ZERO = new BigDecimal("0.0001");

    @AssertTrue(message = "Eşik 0 veya en az 0.0001% olmalı (DB hassasiyeti)")
    public boolean isThresholdGranularityValid() {
        if (deltaThreshold == null) return true;
        if (deltaThreshold.signum() == 0) return true;
        return deltaThreshold.compareTo(MIN_NON_ZERO) >= 0;
    }
}
