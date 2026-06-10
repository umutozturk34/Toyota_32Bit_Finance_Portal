package com.finance.market.macro.service;

import com.finance.market.macro.dto.InflationRate;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.repository.MacroIndicatorPointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MacroInflationRateServiceTest {

    @Mock private MacroIndicatorPointRepository pointRepository;
    @Mock private MacroIndicator indicator;
    @InjectMocks private MacroInflationRateService service;

    private MacroIndicatorPoint point(LocalDate date, String value) {
        MacroIndicatorPoint p = mock(MacroIndicatorPoint.class);
        when(p.getObservedAt()).thenReturn(date);
        when(p.getValue()).thenReturn(new BigDecimal(value));
        return p;
    }

    private void givenInflationIndex(String lastValue, LocalDate lastDate) {
        when(indicator.getCategory()).thenReturn(MacroCategory.INFLATION);
        when(indicator.getUnit()).thenReturn(MacroUnit.INDEX);
        when(indicator.getLastValue()).thenReturn(lastValue == null ? null : new BigDecimal(lastValue));
        when(indicator.getLastDate()).thenReturn(lastDate);
    }

    private void givenPriors(List<MacroIndicatorPoint> priors) {
        when(pointRepository.findByIndicatorAndObservedAtBetweenOrderByObservedAtAsc(eq(indicator), any(), any()))
                .thenReturn(priors);
    }

    @Test
    void shouldDeriveYoyAndMom_fromCpiIndexSeries() {
        // Arrange
        givenInflationIndex("4097.55", LocalDate.of(2026, 5, 31));
        givenPriors(List.of(
                point(LocalDate.of(2025, 5, 31), "3089.74"),
                point(LocalDate.of(2026, 4, 30), "4028.47")));

        // Act
        InflationRate rate = service.compute(indicator);

        // Assert
        assertThat(rate.yoyChangePct()).isEqualByComparingTo("32.62");
        assertThat(rate.momChangePct()).isEqualByComparingTo("1.71");
    }

    @Test
    void shouldReturnEmpty_whenCategoryNotInflation() {
        // Arrange
        when(indicator.getCategory()).thenReturn(MacroCategory.RATES);

        // Act
        InflationRate rate = service.compute(indicator);

        // Assert
        assertThat(rate).isEqualTo(InflationRate.EMPTY);
        verifyNoInteractions(pointRepository);
    }

    @Test
    void shouldReturnEmpty_whenUnitNotIndex() {
        // Arrange
        when(indicator.getCategory()).thenReturn(MacroCategory.INFLATION);
        when(indicator.getUnit()).thenReturn(MacroUnit.PERCENT);

        // Act
        InflationRate rate = service.compute(indicator);

        // Assert
        assertThat(rate).isEqualTo(InflationRate.EMPTY);
        verifyNoInteractions(pointRepository);
    }

    @Test
    void shouldReturnEmpty_whenNoLatestValue() {
        // Arrange
        when(indicator.getCategory()).thenReturn(MacroCategory.INFLATION);
        when(indicator.getUnit()).thenReturn(MacroUnit.INDEX);
        when(indicator.getLastValue()).thenReturn(null);

        // Act
        InflationRate rate = service.compute(indicator);

        // Assert
        assertThat(rate).isEqualTo(InflationRate.EMPTY);
        verifyNoInteractions(pointRepository);
    }

    @Test
    void shouldLeaveYoyNull_whenNoYearAgoObservationWithinTolerance() {
        // Arrange — only a one-month-prior point exists; the year-ago slot is empty
        givenInflationIndex("4097.55", LocalDate.of(2026, 5, 31));
        givenPriors(List.of(point(LocalDate.of(2026, 4, 30), "4028.47")));

        // Act
        InflationRate rate = service.compute(indicator);

        // Assert
        assertThat(rate.yoyChangePct()).isNull();
        assertThat(rate.momChangePct()).isEqualByComparingTo("1.71");
    }

    @Test
    void shouldLeaveYoyNull_whenYearAgoBaseIsZero() {
        // Arrange
        givenInflationIndex("4097.55", LocalDate.of(2026, 5, 31));
        givenPriors(List.of(point(LocalDate.of(2025, 5, 31), "0")));

        // Act
        InflationRate rate = service.compute(indicator);

        // Assert
        assertThat(rate.yoyChangePct()).isNull();
    }
}
