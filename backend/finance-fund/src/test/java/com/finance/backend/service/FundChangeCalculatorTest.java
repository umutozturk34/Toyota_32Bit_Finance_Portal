package com.finance.backend.service;

import com.finance.backend.model.FundCandle;
import com.finance.backend.repository.FundCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundChangeCalculatorTest {

    @Mock
    private FundCandleRepository fundCandleRepository;

    private FundChangeCalculator calculator;

    @BeforeEach
    void setUp() {
        com.finance.backend.config.AppProperties props = new com.finance.backend.config.AppProperties();
        calculator = new FundChangeCalculator(fundCandleRepository, props);
    }

    @Test
    void calculatesPositiveChangePercent() {
        when(fundCandleRepository.findTop2ByFundCodeOrderByCandleDateDesc("AAK"))
                .thenReturn(List.of(stubCandle(new BigDecimal("110.0000")), stubCandle(new BigDecimal("100.0000"))));

        BigDecimal result = calculator.calculateChangePercent("AAK", new BigDecimal("110.0000"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void calculatesNegativeChangePercent() {
        when(fundCandleRepository.findTop2ByFundCodeOrderByCandleDateDesc("BBK"))
                .thenReturn(List.of(stubCandle(new BigDecimal("90.0000")), stubCandle(new BigDecimal("100.0000"))));

        BigDecimal result = calculator.calculateChangePercent("BBK", new BigDecimal("90.0000"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("-10.0000"));
    }

    @Test
    void nullCurrentPriceReturnsZero() {
        BigDecimal result = calculator.calculateChangePercent("AAK", null);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void zeroCurrentPriceReturnsZero() {
        BigDecimal result = calculator.calculateChangePercent("AAK", BigDecimal.ZERO);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fewerThanTwoCandlesReturnsZero() {
        when(fundCandleRepository.findTop2ByFundCodeOrderByCandleDateDesc("NEW"))
                .thenReturn(List.of(stubCandle(new BigDecimal("50.0000"))));

        BigDecimal result = calculator.calculateChangePercent("NEW", new BigDecimal("50.0000"));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nullPreviousPriceReturnsZero() {
        when(fundCandleRepository.findTop2ByFundCodeOrderByCandleDateDesc("NUL"))
                .thenReturn(List.of(stubCandle(new BigDecimal("100.0000")), stubCandle(null)));

        BigDecimal result = calculator.calculateChangePercent("NUL", new BigDecimal("100.0000"));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void zeroPreviousPriceReturnsZero() {
        when(fundCandleRepository.findTop2ByFundCodeOrderByCandleDateDesc("ZER"))
                .thenReturn(List.of(stubCandle(new BigDecimal("100.0000")), stubCandle(BigDecimal.ZERO)));

        BigDecimal result = calculator.calculateChangePercent("ZER", new BigDecimal("100.0000"));

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fractionalChangePercentRoundedToScale4() {
        when(fundCandleRepository.findTop2ByFundCodeOrderByCandleDateDesc("FRC"))
                .thenReturn(List.of(stubCandle(new BigDecimal("103.0000")), stubCandle(new BigDecimal("100.0000"))));

        BigDecimal result = calculator.calculateChangePercent("FRC", new BigDecimal("103.0000"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("3.0000"));
        assertThat(result.scale()).isEqualTo(4);
    }

    private FundCandle stubCandle(BigDecimal price) {
        FundCandle candle = new FundCandle();
        candle.setPrice(price);
        return candle;
    }
}
