package com.finance.app.analytics.dto;

import java.time.LocalDate;
import java.time.Period;

/**
 * The windows the asset-returns view reports, each a UI token plus the {@link Period} subtracted from the
 * window end to get the window start.
 */
public enum ReturnPeriod {
    ONE_WEEK("1W", Period.ofWeeks(1)),
    ONE_MONTH("1M", Period.ofMonths(1)),
    THREE_MONTHS("3M", Period.ofMonths(3)),
    SIX_MONTHS("6M", Period.ofMonths(6)),
    ONE_YEAR("1Y", Period.ofYears(1)),
    THREE_YEARS("3Y", Period.ofYears(3)),
    FIVE_YEARS("5Y", Period.ofYears(5));

    private final String token;
    private final Period period;

    ReturnPeriod(String token, Period period) {
        this.token = token;
        this.period = period;
    }

    public String token() {
        return token;
    }

    /** The window start for a window ending on {@code end}. */
    public LocalDate startFrom(LocalDate end) {
        return end.minus(period);
    }
}
