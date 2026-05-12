package com.finance.market.core.util;

import com.finance.market.core.dto.external.YahooCandleDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCrossCalculatorTest {

    @Test
    void buildTryCandles_multipliesPairByUsdTryPerDate() {
        LocalDateTime day = LocalDateTime.of(2026, 1, 5, 0, 0);
        YahooCandleDto pair = candle(day, "1.10", "1.12", "1.08", "1.11", 1000L);
        YahooCandleDto usdtry = candle(day, "30", "31", "29", "30.5", 0L);

        List<YahooCandleDto> result = PriceCrossCalculator.buildTryCandles(
                List.of(pair), Map.of("2026-01-05", usdtry), 2);

        assertThat(result).hasSize(1);
        YahooCandleDto out = result.getFirst();
        assertThat(out.open()).isEqualByComparingTo(new BigDecimal("33.00"));
        assertThat(out.close()).isEqualByComparingTo(new BigDecimal("33.86"));
    }

    @Test
    void buildTryCandles_skipsPairWithMissingUsdTryDate() {
        LocalDateTime day = LocalDateTime.of(2026, 1, 5, 0, 0);
        YahooCandleDto pair = candle(day, "1.10", "1.12", "1.08", "1.11", 0L);

        List<YahooCandleDto> result = PriceCrossCalculator.buildTryCandles(
                List.of(pair), Map.of(), 2);

        assertThat(result).isEmpty();
    }

    @Test
    void buildTryCandles_skipsCandleWhenAnyOhlcMultiplyIsNull() {
        LocalDateTime day = LocalDateTime.of(2026, 1, 5, 0, 0);
        YahooCandleDto pair = new YahooCandleDto(day, null,
                new BigDecimal("1.12"), new BigDecimal("1.08"), new BigDecimal("1.11"), 0L);
        YahooCandleDto usdtry = candle(day, "30", "31", "29", "30.5", 0L);

        List<YahooCandleDto> result = PriceCrossCalculator.buildTryCandles(
                List.of(pair), Map.of("2026-01-05", usdtry), 2);

        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "10.0, 2.0, 4, 5.0000",
            "1.0, 3.0, 4, 0.3333",
            "0, 5.0, 2, 0.00"
    })
    void safeDivide_returnsScaledQuotient_whenInputsValid(String n, String d, int scale, String expected) {
        BigDecimal result = PriceCrossCalculator.safeDivide(new BigDecimal(n), new BigDecimal(d), scale);

        assertThat(result).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void safeDivide_returnsNull_whenAnyInputNullOrDenominatorZero() {
        assertThat(PriceCrossCalculator.safeDivide(null, BigDecimal.ONE, 2)).isNull();
        assertThat(PriceCrossCalculator.safeDivide(BigDecimal.ONE, null, 2)).isNull();
        assertThat(PriceCrossCalculator.safeDivide(BigDecimal.ONE, BigDecimal.ZERO, 2)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "2.5, 4.0, 2, 10.00",
            "0.10, 30.00, 4, 3.0000",
            "-1.5, 2.0, 2, -3.00"
    })
    void safeMultiply_returnsScaledProduct_whenInputsValid(String a, String b, int scale, String expected) {
        BigDecimal result = PriceCrossCalculator.safeMultiply(new BigDecimal(a), new BigDecimal(b), scale);

        assertThat(result).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void safeMultiply_returnsNull_whenAnyInputIsNull() {
        assertThat(PriceCrossCalculator.safeMultiply(null, BigDecimal.ONE, 2)).isNull();
        assertThat(PriceCrossCalculator.safeMultiply(BigDecimal.ONE, null, 2)).isNull();
    }

    private static YahooCandleDto candle(LocalDateTime ts, String o, String h, String l, String c, Long vol) {
        return new YahooCandleDto(ts,
                new BigDecimal(o), new BigDecimal(h), new BigDecimal(l), new BigDecimal(c), vol);
    }
}
