package com.finance.market.macro.dto.response;

import com.finance.market.macro.model.DepositMaturity;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroFrequency;
import com.finance.market.macro.model.MacroUnit;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * API payload describing a macro/economic indicator's definition together with its latest cached
 * observation, used to render the macro indicators listing without loading the full time series.
 *
 * @param code      stable EVDS-derived identifier of the indicator
 * @param label     human-readable display name
 * @param category  grouping (rates, inflation, deposit)
 * @param unit      unit of the values (e.g. percent, currency amount)
 * @param frequency publish cadence (daily, weekly, monthly)
 * @param currency  currency code when the indicator is monetary, otherwise {@code null}
 * @param maturity  deposit maturity bucket for deposit indicators, otherwise {@code null}
 * @param prominent whether the indicator is highlighted in summary/featured views
 * @param lastValue most recent observed value, or {@code null} if no observation exists yet
 * @param lastDate  date of the most recent observation, or {@code null} if none exists yet
 */
public record MacroIndicatorResponse(
        String code,
        String label,
        MacroCategory category,
        MacroUnit unit,
        MacroFrequency frequency,
        String currency,
        DepositMaturity maturity,
        boolean prominent,
        BigDecimal lastValue,
        LocalDate lastDate
) { }
