package com.finance.portfolio.derivative.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

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
