package com.finance.market.macro.config;

import com.finance.market.macro.model.DepositMaturity;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroFrequency;
import com.finance.market.macro.model.MacroUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.List;

/**
 * Externalised configuration ({@code app.macro.*}) for macroeconomic-indicator ingestion.
 *
 * <p>Defines the historical backfill anchor, the batch size and per-window day cap used
 * when fetching observations from the provider, and client-facing defaults (history span
 * in years and response batch size) that are normalised in the compact constructor when
 * left unset. {@code indicators} declares the catalogue of tracked series.
 */
@ConfigurationProperties(prefix = "app.macro")
public record MacroProperties(
        LocalDate backfillStartDate,
        int batchSize,
        int maxDaysPerWindow,
        Integer defaultHistoryYears,
        Integer clientDefaultBatchSize,
        List<IndicatorDefinition> indicators
) {

    public MacroProperties {
        if (defaultHistoryYears == null) defaultHistoryYears = 5;
        if (clientDefaultBatchSize == null) clientDefaultBatchSize = 25;
    }
    /**
     * Declarative description of a single tracked macroeconomic series: its provider
     * {@code code}, display {@code label}, classification ({@link MacroCategory},
     * {@link MacroUnit}, {@link MacroFrequency}), optional reporting {@code currency} and
     * deposit {@link DepositMaturity}, and a {@code prominent} flag marking it for
     * surfacing in summary views.
     */
    public record IndicatorDefinition(
            String code,
            String label,
            MacroCategory category,
            MacroUnit unit,
            MacroFrequency frequency,
            String currency,
            DepositMaturity maturity,
            boolean prominent
    ) { }
}
