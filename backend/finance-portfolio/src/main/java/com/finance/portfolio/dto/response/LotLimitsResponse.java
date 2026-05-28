package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Allowed bounds for lot entry (date/price/quantity) surfaced to the UI for client-side validation. */
public record LotLimitsResponse(
        LocalDate minEntryDate,
        LocalDate maxEntryDate,
        BigDecimal minPriceTry,
        BigDecimal maxPriceTry,
        BigDecimal minQuantity,
        BigDecimal maxQuantity
) {}
