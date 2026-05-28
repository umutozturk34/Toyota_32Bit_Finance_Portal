package com.finance.app.analytics.dto.request;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.common.model.Currency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Scenario request: invest {@code amount} at {@code startDate} across up to six instruments, valued in
 * {@code targetCurrency} (TRY when null). {@code endDate} defaults to today.
 */
public record ScenarioRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotEmpty @Size(max = 6) @Valid List<AnalyticsInstrument> instruments,
        Currency targetCurrency) {

    public ScenarioRequest(BigDecimal amount, LocalDate startDate, LocalDate endDate,
                           List<AnalyticsInstrument> instruments) {
        this(amount, startDate, endDate, instruments, null);
    }
}
