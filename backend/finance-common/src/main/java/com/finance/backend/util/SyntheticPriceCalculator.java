package com.finance.backend.util;

import com.finance.backend.dto.external.YahooCandleDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SyntheticPriceCalculator {

    private SyntheticPriceCalculator() {}

    public static BigDecimal calculateSyntheticPrice(BigDecimal pairPrice,
                                                     BigDecimal usdtryPrice,
                                                     boolean isUsdBase,
                                                     int scale) {
        if (pairPrice == null || pairPrice.signum() == 0 || usdtryPrice == null) {
            return null;
        }
        return isUsdBase
                ? safeDivide(usdtryPrice, pairPrice, scale)
                : safeMultiply(usdtryPrice, pairPrice, scale);
    }

    public static BigDecimal calculateSyntheticPreviousClose(BigDecimal pairPreviousClose,
                                                             BigDecimal usdtryPrice,
                                                             BigDecimal usdtryChange,
                                                             boolean isUsdBase,
                                                             int scale) {
        if (usdtryPrice == null) return null;
        BigDecimal usdtryPreviousClose = usdtryChange != null
                ? usdtryPrice.subtract(usdtryChange)
                : usdtryPrice;
        if (usdtryPreviousClose.signum() <= 0) return null;
        return calculateSyntheticPrice(pairPreviousClose, usdtryPreviousClose, isUsdBase, scale);
    }

    public static List<YahooCandleDto> buildSyntheticCandles(List<YahooCandleDto> pairCandles,
                                                              Map<String, YahooCandleDto> usdtryCandleByDate,
                                                              boolean isUsdBase,
                                                              int scale) {
        List<YahooCandleDto> synthetic = new ArrayList<>(pairCandles.size());
        for (YahooCandleDto pair : pairCandles) {
            String dateKey = pair.candleDate().toLocalDate().toString();
            YahooCandleDto usdtryCandle = usdtryCandleByDate.get(dateKey);
            if (usdtryCandle == null) continue;
            YahooCandleDto converted = isUsdBase
                    ? buildUsdBaseCandle(pair, usdtryCandle, scale)
                    : buildNonUsdBaseCandle(pair, usdtryCandle, scale);
            if (converted != null) {
                synthetic.add(converted);
            }
        }
        return synthetic;
    }

    private static YahooCandleDto buildUsdBaseCandle(YahooCandleDto pair, YahooCandleDto usdtry, int scale) {
        BigDecimal open = safeDivide(usdtry.open(), pair.open(), scale);
        BigDecimal high = safeDivide(usdtry.high(), pair.low(), scale);
        BigDecimal low = safeDivide(usdtry.low(), pair.high(), scale);
        BigDecimal close = safeDivide(usdtry.close(), pair.close(), scale);
        if (open == null || high == null || low == null || close == null) return null;
        return new YahooCandleDto(pair.candleDate(), open, maxOf(open, high, close),
                minOf(open, low, close), close, pair.volume());
    }

    private static YahooCandleDto buildNonUsdBaseCandle(YahooCandleDto pair, YahooCandleDto usdtry, int scale) {
        BigDecimal open = safeMultiply(usdtry.open(), pair.open(), scale);
        BigDecimal high = safeMultiply(usdtry.high(), pair.high(), scale);
        BigDecimal low = safeMultiply(usdtry.low(), pair.low(), scale);
        BigDecimal close = safeMultiply(usdtry.close(), pair.close(), scale);
        if (open == null || high == null || low == null || close == null) return null;
        return new YahooCandleDto(pair.candleDate(), open, maxOf(open, high, close),
                minOf(open, low, close), close, pair.volume());
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
