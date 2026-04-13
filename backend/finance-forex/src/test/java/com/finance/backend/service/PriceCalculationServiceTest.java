package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.model.ForexCandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCalculationServiceTest {

    private PriceCalculationService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        AppProperties.Forex forex = new AppProperties.Forex();
        forex.setSpreadRate(new BigDecimal("0.01"));
        props.setForex(forex);
        props.setScale(4);
        service = new PriceCalculationService(props);
    }

    @Test
    void buildSyntheticCandlesUsdBaseInvertsDivision() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 1, 0, 0);
        YahooCandleDto pairCandle = new YahooCandleDto(date,
                new BigDecimal("1.0800"), new BigDecimal("1.0900"),
                new BigDecimal("1.0700"), new BigDecimal("1.0850"), 1000L);
        ForexCandle usdtry = stubForexCandle(
                new BigDecimal("38.0000"), new BigDecimal("38.5000"),
                new BigDecimal("37.5000"), new BigDecimal("38.2000"));

        List<YahooCandleDto> result = service.buildSyntheticCandles(
                List.of(pairCandle), Map.of("2025-06-01", usdtry), true);

        assertThat(result).hasSize(1);
        YahooCandleDto synthetic = result.get(0);
        BigDecimal expectedOpen = new BigDecimal("38.0000").divide(new BigDecimal("1.0800"), 4, RoundingMode.HALF_UP);
        BigDecimal expectedClose = new BigDecimal("38.2000").divide(new BigDecimal("1.0850"), 4, RoundingMode.HALF_UP);
        assertThat(synthetic.open()).isEqualByComparingTo(expectedOpen);
        assertThat(synthetic.close()).isEqualByComparingTo(expectedClose);
    }

    @Test
    void buildSyntheticCandlesNonUsdBaseMultiplies() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 1, 0, 0);
        YahooCandleDto pairCandle = new YahooCandleDto(date,
                new BigDecimal("0.9200"), new BigDecimal("0.9300"),
                new BigDecimal("0.9100"), new BigDecimal("0.9250"), 500L);
        ForexCandle usdtry = stubForexCandle(
                new BigDecimal("38.0000"), new BigDecimal("38.5000"),
                new BigDecimal("37.5000"), new BigDecimal("38.2000"));

        List<YahooCandleDto> result = service.buildSyntheticCandles(
                List.of(pairCandle), Map.of("2025-06-01", usdtry), false);

        assertThat(result).hasSize(1);
        YahooCandleDto synthetic = result.get(0);
        BigDecimal expectedOpen = new BigDecimal("38.0000").multiply(new BigDecimal("0.9200")).setScale(4, RoundingMode.HALF_UP);
        assertThat(synthetic.open()).isEqualByComparingTo(expectedOpen);
    }

    @Test
    void buildSyntheticCandlesSkipsMissingUsdtryDate() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 1, 0, 0);
        YahooCandleDto pairCandle = new YahooCandleDto(date,
                new BigDecimal("1.08"), new BigDecimal("1.09"),
                new BigDecimal("1.07"), new BigDecimal("1.085"), 100L);

        List<YahooCandleDto> result = service.buildSyntheticCandles(
                List.of(pairCandle), Map.of(), true);

        assertThat(result).isEmpty();
    }

    @Test
    void buildSyntheticCandlesSkipsWhenDivisionByZero() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 1, 0, 0);
        YahooCandleDto pairCandle = new YahooCandleDto(date,
                BigDecimal.ZERO, new BigDecimal("1.09"),
                new BigDecimal("1.07"), new BigDecimal("1.085"), 100L);
        ForexCandle usdtry = stubForexCandle(
                new BigDecimal("38.0000"), new BigDecimal("38.5000"),
                new BigDecimal("37.5000"), new BigDecimal("38.2000"));

        List<YahooCandleDto> result = service.buildSyntheticCandles(
                List.of(pairCandle), Map.of("2025-06-01", usdtry), true);

        assertThat(result).isEmpty();
    }

    @Test
    void applySyntheticSnapshotUsdBaseDivides() {
        com.finance.backend.model.Forex forex = com.finance.backend.model.Forex.builder()
                .currencyCode("EUR").build();
        YahooQuoteDto pairQuote = new YahooQuoteDto(new BigDecimal("1.0800"), new BigDecimal("1.0750"));
        BigDecimal usdtryPrice = new BigDecimal("38.0000");
        BigDecimal usdtryChange = new BigDecimal("0.5000");

        service.applySyntheticSnapshot(forex, pairQuote, usdtryPrice, usdtryChange, true);

        BigDecimal expectedPrice = usdtryPrice.divide(new BigDecimal("1.0800"), 4, RoundingMode.HALF_UP);
        assertThat(forex.getCurrentPrice()).isEqualByComparingTo(expectedPrice);
    }

    @Test
    void applySyntheticSnapshotNonUsdBaseMultiplies() {
        com.finance.backend.model.Forex forex = com.finance.backend.model.Forex.builder()
                .currencyCode("GBP").build();
        YahooQuoteDto pairQuote = new YahooQuoteDto(new BigDecimal("0.7900"), new BigDecimal("0.7850"));
        BigDecimal usdtryPrice = new BigDecimal("38.0000");

        service.applySyntheticSnapshot(forex, pairQuote, usdtryPrice, null, false);

        BigDecimal expectedPrice = usdtryPrice.multiply(new BigDecimal("0.7900")).setScale(4, RoundingMode.HALF_UP);
        assertThat(forex.getCurrentPrice()).isEqualByComparingTo(expectedPrice);
    }

    @Test
    void applySyntheticSnapshotZeroPairPriceDoesNothing() {
        com.finance.backend.model.Forex forex = com.finance.backend.model.Forex.builder()
                .currencyCode("CHF").currentPrice(new BigDecimal("42.0000")).build();
        YahooQuoteDto pairQuote = new YahooQuoteDto(BigDecimal.ZERO, null);

        service.applySyntheticSnapshot(forex, pairQuote, new BigDecimal("38.0000"), null, true);

        assertThat(forex.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("42.0000"));
    }

    @Test
    void applySyntheticSnapshotNullPairPriceDoesNothing() {
        com.finance.backend.model.Forex forex = com.finance.backend.model.Forex.builder()
                .currencyCode("JPY").currentPrice(new BigDecimal("0.2500")).build();
        YahooQuoteDto pairQuote = new YahooQuoteDto(null, null);

        service.applySyntheticSnapshot(forex, pairQuote, new BigDecimal("38.0000"), null, true);

        assertThat(forex.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("0.2500"));
    }

    @Test
    void buildSyntheticCandlesHighIsMaxOfOpenHighClose() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 1, 0, 0);
        YahooCandleDto pairCandle = new YahooCandleDto(date,
                new BigDecimal("1.0000"), new BigDecimal("1.0000"),
                new BigDecimal("1.0000"), new BigDecimal("1.0000"), 100L);
        ForexCandle usdtry = stubForexCandle(
                new BigDecimal("38.0000"), new BigDecimal("40.0000"),
                new BigDecimal("36.0000"), new BigDecimal("39.0000"));

        List<YahooCandleDto> result = service.buildSyntheticCandles(
                List.of(pairCandle), Map.of("2025-06-01", usdtry), false);

        assertThat(result).hasSize(1);
        YahooCandleDto s = result.get(0);
        assertThat(s.high()).isGreaterThanOrEqualTo(s.open());
        assertThat(s.high()).isGreaterThanOrEqualTo(s.close());
        assertThat(s.low()).isLessThanOrEqualTo(s.open());
        assertThat(s.low()).isLessThanOrEqualTo(s.close());
    }

    private ForexCandle stubForexCandle(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        return ForexCandle.builder()
                .open(open).high(high).low(low).close(close)
                .build();
    }
}
