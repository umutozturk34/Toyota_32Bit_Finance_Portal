package com.finance.portfolio.derivative.dto.request;

import com.finance.portfolio.derivative.model.DerivativeDirection;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to open a derivative position. Prices are optional: when omitted the server resolves the
 * historical price; when given (in {@code priceCurrency}) they are converted to TRY at their date.
 * Supplying close fields opens the position already closed.
 */
public record OpenDerivativePositionRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "^[A-Za-z0-9._=-]{1,32}$") String contractSymbol,
        @NotNull DerivativeDirection direction,
        @NotNull @PastOrPresent LocalDate entryDate,
        @DecimalMin(value = "0", inclusive = true) @DecimalMax("1000000000000") @Digits(integer = 15, fraction = 8) BigDecimal entryPrice,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000000") @Digits(integer = 12, fraction = 4) BigDecimal quantityLot,
        @PastOrPresent LocalDate closeDate,
        @DecimalMin(value = "0", inclusive = true) @DecimalMax("1000000000000") @Digits(integer = 15, fraction = 8) BigDecimal closePrice,
        @Pattern(regexp = "^[A-Z]{3}$") String priceCurrency
) {
    public OpenDerivativePositionRequest(String contractSymbol, DerivativeDirection direction,
                                          LocalDate entryDate, BigDecimal entryPrice,
                                          BigDecimal quantityLot, LocalDate closeDate, BigDecimal closePrice) {
        this(contractSymbol, direction, entryDate, entryPrice, quantityLot, closeDate, closePrice, null);
    }
}
