package com.finance.portfolio.derivative.dto.request;

import com.finance.portfolio.derivative.model.DerivativeDirection;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request to edit an open derivative's entry; {@code entryPrice} (in {@code priceCurrency}) converts to TRY, or is resolved from history when null. */
public record UpdateDerivativePositionRequest(
        @NotNull DerivativeDirection direction,
        @NotNull @PastOrPresent LocalDate entryDate,
        @DecimalMin(value = "0", inclusive = true) @DecimalMax("1000000000000") @Digits(integer = 15, fraction = 4) BigDecimal entryPrice,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000000") @Digits(integer = 12, fraction = 4) BigDecimal quantityLot,
        @Pattern(regexp = "^[A-Z]{3}$") String priceCurrency
) {
    public UpdateDerivativePositionRequest(DerivativeDirection direction, LocalDate entryDate,
                                            BigDecimal entryPrice, BigDecimal quantityLot) {
        this(direction, entryDate, entryPrice, quantityLot, null);
    }
}
