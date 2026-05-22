package com.finance.portfolio.dto.response;

import java.math.BigDecimal;

public record CurrencyFramePct(
        BigDecimal pnlPercent,
        BigDecimal dailyPnlPercent
) {}
