package com.finance.market.macro.config;

import com.finance.market.macro.model.DepositMaturity;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroFrequency;
import com.finance.market.macro.model.MacroUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.List;

@ConfigurationProperties(prefix = "app.macro")
public record MacroProperties(
        LocalDate backfillStartDate,
        int batchSize,
        int maxDaysPerWindow,
        List<IndicatorDefinition> indicators
) {
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
