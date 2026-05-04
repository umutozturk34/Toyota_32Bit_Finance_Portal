package com.finance.common.util;

import com.finance.common.dto.external.YahooCandleDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticPriceCalculatorTest {

    private static final int SCALE = 4;

    @Test
    void buildSyntheticCandlesUsdBaseInvertsDivision() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 1, 0, 0);
        YahooCandleDto pairCandle = new YahooCandleDto(date,
                new BigDecimal("1.0800"), new BigDecimal("1.0900"),
                new BigDecimal("1.0700"), new BigDecimal("1.0850"), 1000L);
        YahooCandleDto usdtry = new YahooCandleDto(date,
                new BigDecimal("38.0000"), new BigDecimal("38.5000"),
                new BigDecimal("37.5000"), new BigDecimal("38.2000"), null);

        List<YahooCandleDto> result = SyntheticPriceCalculator.buildSyntheticCandles(
                List.of(pairCandle), Map.of("2025-06-01", usdtry), true, SCALE);

        assertThat(result).hasSize(1);
        YahooCandleDto synthetic = result.get(0);
        BigDecimal expectedOpen = new BigDecimal("38.0000").divide(new BigDecimal("1.0800"), SCALE, RoundingMode.HALF_UP);
        BigDecimal expectedClose = new BigDecimal("38.2000").divide(new BigDecimal("1.0850"), SCALE, RoundingMode.HALF_UP);
        assertThat(synthetic.open()).isEqualByComparingTo(expectedOpen);
        assertThat(synthetic.close()).isEqualByComparingTo(expectedClose);
    }

    @Test
    void buildSyntheticCandlesNonUsdBaseMultiplies() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 1, 0, 0);
        YahooCandleDto pairCandle = new YahooCandleDto(date,
                new BigDecimal("0.9200"), new BigDecimal("0.9300"),
                new BigDecimal("0.9100"), new BigDecimal("0.9250"), 500L);
        YahooCandleDto usdtry = new YahooCandleDto(date,
                new BigDecimal("38.0000"), new BigDecimal("38.5000"),
                new BigDecimal("37.5000"), new BigDecimal("38.2000"), null);

        List<YahooCandleDto> result = SyntheticPriceCalculator.buildSyntheticCandles(
                List.of(pairCandle), Map.of("2025-06-01", usdtry), false, SCALE);

        assertThat(result).hasSize(1);
        YahooCandleDto synthetic = result.get(0);
        BigDecimal expectedOpen = new BigDecimal("38.0000").multiply(new BigDecimal("0.9200")).setScale(SCALE, RoundingMode.HALF_UP);
        assertThat(synthetic.open()).isEqualByComparingTo(expectedOpen);
    }

    @Test
    void buildSyntheticCandlesSkipsMissingUsdtryDate() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 1, 0, 0);
        YahooCandleDto pairCandle = new YahooCandleDto(date,
                new BigDecimal("1.08"), new BigDecimal("1.09"),
                new BigDecimal("1.07"), new BigDecimal("1.085"), 100L);

        List<YahooCandleDto> result = SyntheticPriceCalculator.buildSyntheticCandles(
                List.of(pairCandle), Map.of(), true, SCALE);

        assertThat(result).isEmpty();
    }

    @Test
    void buildSyntheticCandlesSkipsWhenDivisionByZero() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 1, 0, 0);
        YahooCandleDto pairCandle = new YahooCandleDto(date,
                BigDecimal.ZERO, new BigDecimal("1.09"),
                new BigDecimal("1.07"), new BigDecimal("1.085"), 100L);
        YahooCandleDto usdtry = new YahooCandleDto(date,
                new BigDecimal("38.0000"), new BigDecimal("38.5000"),
                new BigDecimal("37.5000"), new BigDecimal("38.2000"), null);

        List<YahooCandleDto> result = SyntheticPriceCalculator.buildSyntheticCandles(
                List.of(pairCandle), Map.of("2025-06-01", usdtry), true, SCALE);

        assertThat(result).isEmpty();
    }

    @Test
    void calculateSyntheticPriceUsdBaseDivides() {
        BigDecimal pairPrice = new BigDecimal("1.0800");
        BigDecimal usdtryPrice = new BigDecimal("38.0000");

        BigDecimal result = SyntheticPriceCalculator.calculateSyntheticPrice(pairPrice, usdtryPrice, true, SCALE);

        BigDecimal expected = usdtryPrice.divide(pairPrice, SCALE, RoundingMode.HALF_UP);
        assertThat(result).isEqualByComparingTo(expected);
    }

    @Test
    void calculateSyntheticPriceNonUsdBaseMultiplies() {
        BigDecimal pairPrice = new BigDecimal("0.7900");
        BigDecimal usdtryPrice = new BigDecimal("38.0000");

        BigDecimal result = SyntheticPriceCalculator.calculateSyntheticPrice(pairPrice, usdtryPrice, false, SCALE);

        BigDecimal expected = usdtryPrice.multiply(pairPrice).setScale(SCALE, RoundingMode.HALF_UP);
        assertThat(result).isEqualByComparingTo(expected);
    }

    @Test
    void calculateSyntheticPriceZeroPairReturnsNull() {
        BigDecimal result = SyntheticPriceCalculator.calculateSyntheticPrice(
                BigDecimal.ZERO, new BigDecimal("38.0000"), true, SCALE);
        assertThat(result).isNull();
    }

    @Test
    void calculateSyntheticPriceNullPairReturnsNull() {
        BigDecimal result = SyntheticPriceCalculator.calculateSyntheticPrice(
                null, new BigDecimal("38.0000"), true, SCALE);
        assertThat(result).isNull();
    }

    @Test
    void buildSyntheticCandlesHighIsMaxOfOpenHighClose() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 1, 0, 0);
        YahooCandleDto pairCandle = new YahooCandleDto(date,
                new BigDecimal("1.0000"), new BigDecimal("1.0000"),
                new BigDecimal("1.0000"), new BigDecimal("1.0000"), 100L);
        YahooCandleDto usdtry = new YahooCandleDto(date,
                new BigDecimal("38.0000"), new BigDecimal("40.0000"),
                new BigDecimal("36.0000"), new BigDecimal("39.0000"), null);

        List<YahooCandleDto> result = SyntheticPriceCalculator.buildSyntheticCandles(
                List.of(pairCandle), Map.of("2025-06-01", usdtry), false, SCALE);

        assertThat(result).hasSize(1);
        YahooCandleDto s = result.get(0);
        assertThat(s.high()).isGreaterThanOrEqualTo(s.open());
        assertThat(s.high()).isGreaterThanOrEqualTo(s.close());
        assertThat(s.low()).isLessThanOrEqualTo(s.open());
        assertThat(s.low()).isLessThanOrEqualTo(s.close());
    }
}
