package com.finance.portfolio.dto.response;

import java.math.BigDecimal;

/** Portfolio value and PnL expressed in one currency frame (e.g. USD/EUR/TRY); {@link #empty()} when rates were unavailable. */
public record CurrencyFramePct(
        BigDecimal pnlPercent,
        BigDecimal dailyPnlPercent,
        BigDecimal totalValue,
        BigDecimal totalEntry,
        BigDecimal totalPnl,
        BigDecimal dailyPnl
) {

    public static CurrencyFramePct empty() {
        return new CurrencyFramePct(null, null, null, null, null, null);
    }

    public static CurrencyFramePct ofPctOnly(BigDecimal pnlPct, BigDecimal dailyPct) {
        return new CurrencyFramePct(pnlPct, dailyPct, null, null, null, null);
    }
}
