package com.finance.market.fund.config;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

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
