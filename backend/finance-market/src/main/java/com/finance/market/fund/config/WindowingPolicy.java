package com.finance.market.fund.config;

/**
 * Fund candle fetch windowing parameters distilled from {@link FundProperties}: per-request window
 * size, history span, the candle count above which incremental (vs. full) refresh is used, and the
 * end-of-day cutover hour.
 */
public record WindowingPolicy(
        int windowSizeDays,
        int yearsToFetch,
        int minCandlesForIncremental,
        int eodCutoverHour) {

    public static WindowingPolicy from(FundProperties props) {
        return new WindowingPolicy(
                props.getWindowSizes(),
                props.getYearsToFetch(),
                props.getMinCandlesForIncremental(),
                props.getTefasEodCutoverHour());
    }
}
