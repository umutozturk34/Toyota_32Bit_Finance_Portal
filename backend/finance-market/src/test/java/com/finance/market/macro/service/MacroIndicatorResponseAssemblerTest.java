package com.finance.market.macro.service;

import com.finance.market.macro.dto.InflationRate;
import com.finance.market.macro.dto.response.MacroIndicatorPointResponse;
import com.finance.market.macro.dto.response.MacroIndicatorResponse;
import com.finance.market.macro.mapper.MacroIndicatorResponseMapper;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroFrequency;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.model.MacroUnit;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MacroIndicatorResponseAssemblerTest {

    @Mock private MacroIndicatorResponseMapper mapper;
    @Mock private MacroInflationRateService inflationRateService;
    @Mock private MacroIndicator indicator;
    @InjectMocks private MacroIndicatorResponseAssembler assembler;

    private MacroIndicatorResponse base(String code) {
        return new MacroIndicatorResponse(code, "Label", MacroCategory.INFLATION, MacroUnit.INDEX,
                MacroFrequency.MONTHLY, null, null, true,
                new BigDecimal("4097.55"), LocalDate.of(2026, 5, 31), null, null);
    }

    @Test
    void shouldAttachDerivedRates_whenMappingInflationIndicator() {
        // Arrange
        when(mapper.toResponse(indicator)).thenReturn(base("CPI"));
        when(inflationRateService.compute(indicator))
                .thenReturn(new InflationRate(new BigDecimal("32.62"), new BigDecimal("1.71")));

        // Act
        MacroIndicatorResponse result = assembler.toResponse(indicator);

        // Assert
        assertThat(result.code()).isEqualTo("CPI");
        assertThat(result.yoyChangePct()).isEqualByComparingTo("32.62");
        assertThat(result.momChangePct()).isEqualByComparingTo("1.71");
    }

    @Test
    void shouldLeaveRatesNull_whenIndicatorHasNoDerivableRate() {
        // Arrange
        when(mapper.toResponse(indicator)).thenReturn(base("POLICY"));
        when(inflationRateService.compute(indicator)).thenReturn(InflationRate.EMPTY);

        // Act
        MacroIndicatorResponse result = assembler.toResponse(indicator);

        // Assert
        assertThat(result.yoyChangePct()).isNull();
        assertThat(result.momChangePct()).isNull();
    }

    @Test
    void shouldMapEveryIndicator_whenAssemblingList() {
        // Arrange
        when(mapper.toResponse(indicator)).thenReturn(base("CPI"));
        when(inflationRateService.compute(indicator)).thenReturn(InflationRate.EMPTY);

        // Act
        List<MacroIndicatorResponse> result = assembler.toResponses(List.of(indicator, indicator));

        // Assert
        assertThat(result).hasSize(2);
    }

    @Test
    void shouldDelegatePointMapping_toMapper() {
        // Arrange
        List<MacroIndicatorPoint> points = List.of();
        List<MacroIndicatorPointResponse> mapped =
                List.of(new MacroIndicatorPointResponse(LocalDate.of(2026, 5, 31), new BigDecimal("4097.55")));
        when(mapper.toPointResponses(points)).thenReturn(mapped);

        // Act
        List<MacroIndicatorPointResponse> result = assembler.toPointResponses(points);

        // Assert
        assertThat(result).isEqualTo(mapped);
    }
}
