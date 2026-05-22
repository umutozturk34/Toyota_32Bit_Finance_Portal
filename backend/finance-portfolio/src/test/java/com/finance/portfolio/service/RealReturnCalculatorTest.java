package com.finance.portfolio.service;

import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import com.finance.portfolio.model.PortfolioPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealReturnCalculatorTest {

    @Mock private MacroIndicatorQueryService macroQueryService;

    @InjectMocks
    private RealReturnCalculator calculator;

    @Test
    void shouldReturnEmptyWhenPositionsListIsEmpty() {
        RealReturnCalculator.RealReturnSummary result = calculator.compute(List.of(), new BigDecimal("100000"));

        assertThat(result.realPnlTry()).isNull();
        assertThat(result.realPnlPercent()).isNull();
        assertThat(result.cpiGrowthPercent()).isNull();
    }

    @Test
    void shouldReturnEmptyWhenTotalValueIsNull() {
        PortfolioPosition position = mock(PortfolioPosition.class);

        RealReturnCalculator.RealReturnSummary result = calculator.compute(List.of(position), null);

        assertThat(result.realPnlTry()).isNull();
    }

    @Test
    void shouldComputePositiveRealReturnWhenAssetOutpacesCpi() {
        PortfolioPosition position = positionAt(LocalDateTime.of(2024, 1, 5, 0, 0), new BigDecimal("100000"));
        MacroIndicatorPoint p1 = pointMock(LocalDate.of(2024, 1, 5), new BigDecimal("2000"));
        MacroIndicatorPoint p2 = pointMock(LocalDate.now(), new BigDecimal("2400"));
        wireCpi(List.of(p1, p2));

        RealReturnCalculator.RealReturnSummary result = calculator.compute(List.of(position), new BigDecimal("150000"));

        assertThat(result.cpiGrowthPercent()).isEqualByComparingTo("20.000000");
        assertThat(result.realPnlTry()).isEqualByComparingTo("30000.000000");
        assertThat(result.realPnlPercent()).isEqualByComparingTo("25.000000");
    }

    @Test
    void shouldComputeNegativeRealReturnWhenAssetTrailsInflation() {
        PortfolioPosition position = positionAt(LocalDateTime.of(2024, 1, 5, 0, 0), new BigDecimal("100000"));
        MacroIndicatorPoint p1 = pointMock(LocalDate.of(2024, 1, 5), new BigDecimal("2000"));
        MacroIndicatorPoint p2 = pointMock(LocalDate.now(), new BigDecimal("3000"));
        wireCpi(List.of(p1, p2));

        RealReturnCalculator.RealReturnSummary result = calculator.compute(List.of(position), new BigDecimal("120000"));

        assertThat(result.cpiGrowthPercent()).isEqualByComparingTo("50.000000");
        assertThat(result.realPnlTry()).isLessThan(BigDecimal.ZERO);
        assertThat(result.realPnlPercent()).isLessThan(BigDecimal.ZERO);
    }

    @Test
    void shouldUseEarliestEntryDateAsCpiAnchor() {
        PortfolioPosition older = positionAt(LocalDateTime.of(2023, 6, 1, 0, 0), new BigDecimal("50000"));
        PortfolioPosition newer = positionAt(LocalDateTime.of(2024, 6, 1, 0, 0), new BigDecimal("50000"));
        MacroIndicatorPoint p1 = pointMock(LocalDate.of(2023, 6, 1), new BigDecimal("1500"));
        MacroIndicatorPoint p2 = pointMock(LocalDate.of(2024, 6, 1), new BigDecimal("2000"));
        MacroIndicatorPoint p3 = pointMock(LocalDate.now(), new BigDecimal("2200"));
        wireCpi(List.of(p1, p2, p3));

        RealReturnCalculator.RealReturnSummary result = calculator.compute(List.of(older, newer), new BigDecimal("150000"));

        assertThat(result.cpiGrowthPercent()).isCloseTo(new BigDecimal("46.6667"),
                org.assertj.core.data.Offset.offset(new BigDecimal("0.001")));
    }

    @Test
    void shouldReturnEmptyWhenCpiSeriesUnavailable() {
        PortfolioPosition position = positionAt(LocalDateTime.of(2024, 1, 1, 0, 0), new BigDecimal("100000"));
        when(macroQueryService.findByCode(anyString())).thenThrow(new RuntimeException("no data"));

        RealReturnCalculator.RealReturnSummary result = calculator.compute(List.of(position), new BigDecimal("120000"));

        assertThat(result.realPnlTry()).isNull();
        assertThat(result.cpiGrowthPercent()).isNull();
    }

    private PortfolioPosition positionAt(LocalDateTime entryDate, BigDecimal entryValue) {
        PortfolioPosition position = mock(PortfolioPosition.class);
        org.mockito.Mockito.lenient().when(position.getEntryDate()).thenReturn(entryDate);
        org.mockito.Mockito.lenient().when(position.entryValue()).thenReturn(entryValue);
        return position;
    }

    private void wireCpi(List<MacroIndicatorPoint> points) {
        MacroIndicator indicator = mock(MacroIndicator.class);
        when(macroQueryService.findByCode(anyString())).thenReturn(indicator);
        when(macroQueryService.history(any(), any(), any())).thenReturn(points);
    }

    private MacroIndicatorPoint pointMock(LocalDate date, BigDecimal value) {
        MacroIndicatorPoint p = mock(MacroIndicatorPoint.class);
        org.mockito.Mockito.lenient().when(p.getObservedAt()).thenReturn(date);
        org.mockito.Mockito.lenient().when(p.getValue()).thenReturn(value);
        return p;
    }
}
