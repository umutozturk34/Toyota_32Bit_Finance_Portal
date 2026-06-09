package com.finance.portfolio.derivative.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to close a derivative position. {@code closeQuantityLot} below the held amount triggers a
 * partial close; {@code closePrice} (when given, in {@code priceCurrency}) is converted to TRY at
 * {@code closeDate}, otherwise resolved from history.
 */
public record CloseDerivativePositionRequest(
        @NotNull @PastOrPresent LocalDate closeDate,
        @DecimalMin(value = "0", inclusive = true) @DecimalMax("1000000000000") @Digits(integer = 15, fraction = 4) BigDecimal closePrice,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000000") @Digits(integer = 12, fraction = 4) BigDecimal closeQuantityLot,
        @Pattern(regexp = "^[A-Z]{3}$") String priceCurrency
) {
    public CloseDerivativePositionRequest(LocalDate closeDate, BigDecimal closePrice) {
        this(closeDate, closePrice, null, null);
    }

    public CloseDerivativePositionRequest(LocalDate closeDate, BigDecimal closePrice,
                                           BigDecimal closeQuantityLot) {
        this(closeDate, closePrice, closeQuantityLot, null);
    }
}
