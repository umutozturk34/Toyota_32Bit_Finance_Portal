package com.finance.portfolio.derivative.dto.request;

import com.finance.portfolio.derivative.model.DerivativeDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to open a derivative position. Prices are optional: when omitted the server resolves the
 * historical price; when given (in {@code priceCurrency}) they are converted to TRY at their date.
 * Supplying close fields opens the position already closed.
 */
public record OpenDerivativePositionRequest(
        @NotBlank String contractSymbol,
        @NotNull DerivativeDirection direction,
        @NotNull @PastOrPresent LocalDate entryDate,
        BigDecimal entryPrice,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantityLot,
        @PastOrPresent LocalDate closeDate,
        BigDecimal closePrice,
        String priceCurrency
) {
    public OpenDerivativePositionRequest(String contractSymbol, DerivativeDirection direction,
                                          LocalDate entryDate, BigDecimal entryPrice,
                                          BigDecimal quantityLot, LocalDate closeDate, BigDecimal closePrice) {
        this(contractSymbol, direction, entryDate, entryPrice, quantityLot, closeDate, closePrice, null);
    }
}
