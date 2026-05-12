package com.finance.market.core.util;

import com.finance.market.core.dto.external.YahooCandleDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PriceCrossCalculator {

    private PriceCrossCalculator() {}

    public static List<YahooCandleDto> buildTryCandles(List<YahooCandleDto> pairCandles,
                                                       Map<String, YahooCandleDto> usdtryCandleByDate,
                                                       int scale) {
        List<YahooCandleDto> result = new ArrayList<>(pairCandles.size());
        for (YahooCandleDto pair : pairCandles) {
            String dateKey = pair.candleDate().toLocalDate().toString();
            YahooCandleDto usdtry = usdtryCandleByDate.get(dateKey);
            if (usdtry == null) continue;
            BigDecimal open = safeMultiply(usdtry.open(), pair.open(), scale);
            BigDecimal high = safeMultiply(usdtry.high(), pair.high(), scale);
            BigDecimal low = safeMultiply(usdtry.low(), pair.low(), scale);
            BigDecimal close = safeMultiply(usdtry.close(), pair.close(), scale);
            if (open == null || high == null || low == null || close == null) continue;
            result.add(new YahooCandleDto(pair.candleDate(),
                    open, maxOf(open, high, close), minOf(open, low, close), close, pair.volume()));
        }
        return result;
    }

    public static BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator, int scale) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal safeMultiply(BigDecimal a, BigDecimal b, int scale) {
        if (a == null || b == null) {
            return null;
        }
        return a.multiply(b).setScale(scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal maxOf(BigDecimal a, BigDecimal b, BigDecimal c) {
        BigDecimal max = a;
        if (b.compareTo(max) > 0) max = b;
        if (c.compareTo(max) > 0) max = c;
        return max;
    }

    private static BigDecimal minOf(BigDecimal a, BigDecimal b, BigDecimal c) {
        BigDecimal min = a;
        if (b.compareTo(min) < 0) min = b;
        if (c.compareTo(min) < 0) min = c;
        return min;
    }
}
