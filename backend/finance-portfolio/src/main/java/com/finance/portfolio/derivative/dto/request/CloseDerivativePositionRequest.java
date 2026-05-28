package com.finance.portfolio.derivative.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to close a derivative position. {@code closeQuantityLot} below the held amount triggers a
 * partial close; {@code closePrice} (when given, in {@code priceCurrency}) is converted to TRY at
 * {@code closeDate}, otherwise resolved from history.
 */
public record CloseDerivativePositionRequest(
        @NotNull @PastOrPresent LocalDate closeDate,
        BigDecimal closePrice,
        BigDecimal closeQuantityLot,
        String priceCurrency
) {
    public CloseDerivativePositionRequest(LocalDate closeDate, BigDecimal closePrice) {
        this(closeDate, closePrice, null, null);
    }

    public CloseDerivativePositionRequest(LocalDate closeDate, BigDecimal closePrice,
                                           BigDecimal closeQuantityLot) {
        this(closeDate, closePrice, closeQuantityLot, null);
    }
}
