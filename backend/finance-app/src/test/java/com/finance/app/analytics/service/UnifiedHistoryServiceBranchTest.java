package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedHistoryServiceBranchTest {

    @Mock private HistoricalPricingPort historicalPricingPort;
    @Mock private MacroIndicatorQueryService macroQueryService;
    @Mock private BondRateHistoryRepository bondRateHistoryRepository;

    @InjectMocks
    private UnifiedHistoryService service;

    private static BondRateHistory bondPoint(LocalDate date, String coupon) {
        return BondRateHistory.builder()
                .rateDate(date)
                .couponRate(coupon == null ? null : new BigDecimal(coupon))
                .build();
    }

    @Test
    void shouldReturnBondCouponSeriesWithinWindow_andDropOutOfRangeOrNullRows() {
        // Arrange — repository returns rows spanning before/inside/after the window plus null-field noise.
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.BOND, "TRT123");
        LocalDate from = LocalDate.of(2024, 2, 1);
        LocalDate to = LocalDate.of(2024, 4, 1);
        when(bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc("TRT123")).thenReturn(List.of(
                bondPoint(LocalDate.of(2024, 1, 1), "10"),
                bondPoint(LocalDate.of(2024, 3, 1), "12"),
                bondPoint(LocalDate.of(2024, 5, 1), "14"),
                bondPoint(null, "20"),
                bondPoint(LocalDate.of(2024, 3, 15), null)));

        // Act
        List<HistoryPoint> result = service.getSeries(instrument, from, to);

        // Assert — only the single in-window, fully-populated row survives.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(result.get(0).value()).isEqualByComparingTo("12");
    }

    @Test
    void shouldReturnEmpty_whenBondRepositoryThrows() {
        // Arrange
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.BOND, "BAD");
        when(bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc("BAD"))
                .thenThrow(new RuntimeException("db down"));

        // Act
        List<HistoryPoint> result = service.getSeries(instrument, LocalDate.now().minusYears(1), LocalDate.now());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenMacroIndicatorNotFound() {
        // Arrange — a ResourceNotFoundException is the expected "no such indicator" signal, swallowed to empty.
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.MACRO, "TP.MISSING");
        when(macroQueryService.findByCode("TP.MISSING")).thenThrow(new ResourceNotFoundException("missing"));

        // Act
        List<HistoryPoint> result = service.getSeries(instrument, LocalDate.now().minusYears(1), LocalDate.now());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenMacroHistoryFailsUnexpectedly() {
        // Arrange — a non-ResourceNotFound failure hits the generic catch and still degrades to empty.
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.DEPOSIT, "TP.DEP");
        MacroIndicator indicator = mock(MacroIndicator.class);
        when(macroQueryService.findByCode("TP.DEP")).thenReturn(indicator);
        when(macroQueryService.history(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        // Act
        List<HistoryPoint> result = service.getSeries(instrument, LocalDate.now().minusMonths(6), LocalDate.now());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldExposeMacroSeriesDirectly_viaGetMacroSeries() {
        // Arrange — the public getMacroSeries shortcut routes through the same macro path.
        MacroIndicator indicator = mock(MacroIndicator.class);
        when(macroQueryService.findByCode("TP.CPI")).thenReturn(indicator);
        when(macroQueryService.history(any(), any(), any())).thenReturn(List.of());

        // Act
        List<HistoryPoint> result = service.getMacroSeries("TP.CPI", LocalDate.now().minusYears(1), LocalDate.now());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenPortfolioTypeHasNoRegisteredSource() {
        // Arrange — PORTFOLIO is not market-backed and not handled by macro/bond, hitting the default branch.
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.PORTFOLIO, "PF1");

        // Act
        List<HistoryPoint> result = service.getSeries(instrument, LocalDate.now().minusYears(1), LocalDate.now());

        // Assert
        assertThat(result).isEmpty();
    }
}
