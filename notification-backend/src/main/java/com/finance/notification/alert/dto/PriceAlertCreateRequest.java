package com.finance.notification.alert.dto;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PriceAlertCreateRequest(
        @NotNull MarketType marketType,
        @NotBlank @Size(max = 32) String assetCode,
        @NotNull AlertDirection direction,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal threshold,
        @Size(max = 8) String currency,
        BigDecimal referencePrice
) {
}
