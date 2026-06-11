package com.finance.portfolio.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Request to sell some/all of a spot lot; {@code exitPrice} in {@code priceCurrency} is converted to TRY at {@code exitDate}. */
public record PositionSellRequest(
        @NotNull @Positive @DecimalMax("1000000000") @Digits(integer = 11, fraction = 6) BigDecimal quantity,
        @NotNull @Positive @DecimalMax("1000000000000") @Digits(integer = 15, fraction = 8) BigDecimal exitPrice,
        @NotNull LocalDateTime exitDate,
        @Pattern(regexp = "^[A-Z]{3}$") String priceCurrency
) {
    public PositionSellRequest(BigDecimal quantity, BigDecimal exitPrice, LocalDateTime exitDate) {
        this(quantity, exitPrice, exitDate, null);
    }
}
