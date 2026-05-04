package com.finance.notification.watchlist.dto;

import com.finance.common.model.MarketType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WatchlistItemCreateRequest(
        @NotNull MarketType marketType,
        @NotBlank @Size(max = 32) String assetCode,
        @Size(max = 255) String note,
        @DecimalMin(value = "0", inclusive = false) BigDecimal deltaThreshold
) {
}
