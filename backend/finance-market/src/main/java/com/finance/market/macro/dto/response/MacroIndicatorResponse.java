package com.finance.market.macro.dto.response;

import com.finance.market.macro.model.DepositMaturity;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroFrequency;
import com.finance.market.macro.model.MacroUnit;

import java.math.BigDecimal;
import java.time.LocalDate;

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
