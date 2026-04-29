package com.finance.backend.service;

import com.finance.backend.model.FundCandle;
import com.finance.backend.repository.FundCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundChangeCalculatorTest {

    private static final LocalDateTime TODAY = LocalDateTime.now();

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
        when(fundCandleRepository.findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(eq("AAK"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(stubCandle(new BigDecimal("100.0000"))));

        BigDecimal result = calculator.calculateChangePercent("AAK", new BigDecimal("110.0000"), TODAY);

        assertThat(result).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void calculatesNegativeChangePercent() {
        when(fundCandleRepository.findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(eq("BBK"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(stubCandle(new BigDecimal("100.0000"))));

        BigDecimal result = calculator.calculateChangePercent("BBK", new BigDecimal("90.0000"), TODAY);

        assertThat(result).isEqualByComparingTo(new BigDecimal("-10.0000"));
    }

    @Test
    void nullCurrentPriceReturnsZero() {
        BigDecimal result = calculator.calculateChangePercent("AAK", null, TODAY);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void zeroCurrentPriceReturnsZero() {
        BigDecimal result = calculator.calculateChangePercent("AAK", BigDecimal.ZERO, TODAY);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void noPreviousCandleReturnsZero() {
        when(fundCandleRepository.findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(eq("NEW"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        BigDecimal result = calculator.calculateChangePercent("NEW", new BigDecimal("50.0000"), TODAY);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void zeroPreviousPriceReturnsZero() {
        when(fundCandleRepository.findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(eq("ZER"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(stubCandle(BigDecimal.ZERO)));

        BigDecimal result = calculator.calculateChangePercent("ZER", new BigDecimal("100.0000"), TODAY);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fractionalChangePercentRoundedToScale4() {
        when(fundCandleRepository.findFirstByFundCodeAndCandleDateBeforeOrderByCandleDateDesc(eq("FRC"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(stubCandle(new BigDecimal("100.0000"))));

        BigDecimal result = calculator.calculateChangePercent("FRC", new BigDecimal("103.0000"), TODAY);

        assertThat(result).isEqualByComparingTo(new BigDecimal("3.0000"));
        assertThat(result.scale()).isEqualTo(4);
    }

    private FundCandle stubCandle(BigDecimal price) {
        FundCandle candle = new FundCandle();
        candle.setPrice(price);
        return candle;
    }
}
