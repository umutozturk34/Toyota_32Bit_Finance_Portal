package com.finance.portfolio.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Request to sell some/all of a spot lot; {@code exitPrice} in {@code priceCurrency} is converted to TRY at {@code exitDate}. */
public record PositionSellRequest(
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal exitPrice,
        @NotNull LocalDateTime exitDate,
        String priceCurrency
) {
    public PositionSellRequest(BigDecimal quantity, BigDecimal exitPrice, LocalDateTime exitDate) {
        this(quantity, exitPrice, exitDate, null);
    }
}
