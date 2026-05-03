package com.finance.backend.util;

import com.finance.backend.dto.internal.YahooChartResponse.Quote;

import java.math.BigDecimal;
import java.util.List;

public final class YahooMetaHelpers {

    private YahooMetaHelpers() {
    }

    public static BigDecimal resolvePreviousClose(Quote quote, BigDecimal metaPreviousClose) {
        if (metaPreviousClose != null) return metaPreviousClose;
        if (quote == null || quote.close() == null) return null;
        List<BigDecimal> closes = quote.close();
        for (int i = closes.size() - 2; i >= 0; i--) {
            if (closes.get(i) != null) return closes.get(i);
        }
        return null;
    }

    public static BigDecimal latestNonNull(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return null;
        for (int i = values.size() - 1; i >= 0; i--) {
            if (values.get(i) != null) return values.get(i);
        }
        return null;
    }
}
