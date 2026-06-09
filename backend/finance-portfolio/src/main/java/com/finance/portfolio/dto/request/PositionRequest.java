package com.finance.portfolio.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request to add or update a spot lot. {@code priceCurrency} is the currency of the supplied
 * entry/exit prices; the server converts them to TRY at the respective dates before persisting
 * (null/blank means the prices are already TRY).
 */
public record PositionRequest(
        @NotBlank String assetType,
        @NotBlank String assetCode,
        @NotNull @Positive @DecimalMax("1000000000") BigDecimal quantity,
        @NotNull LocalDateTime entryDate,
        @NotNull @Positive @DecimalMax("1000000000000") BigDecimal entryPrice,
        LocalDateTime exitDate,
        @Positive @DecimalMax("1000000000000") BigDecimal exitPrice,
        String priceCurrency
) {
    public PositionRequest(String assetType, String assetCode, BigDecimal quantity,
                           LocalDateTime entryDate, BigDecimal entryPrice) {
        this(assetType, assetCode, quantity, entryDate, entryPrice, null, null, null);
    }

    public PositionRequest(String assetType, String assetCode, BigDecimal quantity,
                           LocalDateTime entryDate, BigDecimal entryPrice,
                           LocalDateTime exitDate, BigDecimal exitPrice) {
        this(assetType, assetCode, quantity, entryDate, entryPrice, exitDate, exitPrice, null);
    }
}
