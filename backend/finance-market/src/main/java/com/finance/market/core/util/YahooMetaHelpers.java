package com.finance.market.core.util;

import com.finance.market.core.dto.internal.YahooChartResponse.Quote;

import java.math.BigDecimal;
import java.util.List;

/** Helpers for extracting prices from Yahoo chart payloads, tolerating gaps (null bars). */
public final class YahooMetaHelpers {

    private YahooMetaHelpers() {
    }

    /** Previous close from meta if present, else the second-to-last non-null close in the series. */
    public static BigDecimal resolvePreviousClose(Quote quote, BigDecimal metaPreviousClose) {
        if (metaPreviousClose != null) return metaPreviousClose;
        if (quote == null || quote.close() == null) return null;
        List<BigDecimal> closes = quote.close();
        for (int i = closes.size() - 2; i >= 0; i--) {
            if (closes.get(i) != null) return closes.get(i);
        }
        return null;
    }

    /** Last non-null value in the series (most recent valid bar), or null if none. */
    public static BigDecimal latestNonNull(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return null;
        for (int i = values.size() - 1; i >= 0; i--) {
            if (values.get(i) != null) return values.get(i);
        }
        return null;
    }
}
