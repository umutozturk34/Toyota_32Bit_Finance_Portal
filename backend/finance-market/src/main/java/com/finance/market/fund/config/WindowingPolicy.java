package com.finance.market.fund.config;

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
