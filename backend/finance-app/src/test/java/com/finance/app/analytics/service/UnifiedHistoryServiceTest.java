package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedHistoryServiceTest {

    @Mock private HistoricalPricingPort historicalPricingPort;
    @Mock private MacroIndicatorQueryService macroQueryService;

    @InjectMocks
    private UnifiedHistoryService service;

    @Test
    void shouldReturnSortedMarketSeriesForSpot() {
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "THYAO.IS");
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        Map<LocalDate, BigDecimal> unsorted = Map.of(
                LocalDate.of(2024, 3, 1), new BigDecimal("200"),
                LocalDate.of(2024, 1, 15), new BigDecimal("180"),
                LocalDate.of(2024, 5, 20), new BigDecimal("220"));
        when(historicalPricingPort.getPriceSeries(MarketType.STOCK, "THYAO.IS", from, to)).thenReturn(unsorted);

        List<HistoryPoint> result = service.getSeries(instrument, from, to);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(result.get(2).date()).isEqualTo(LocalDate.of(2024, 5, 20));
    }

    @Test
    void shouldReturnEmptyWhenMarketPortHasNoData() {
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.FOREX, "USD");
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(historicalPricingPort.getPriceSeries(any(), anyString(), any(), any())).thenReturn(Map.of());

        List<HistoryPoint> result = service.getSeries(instrument, from, to);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnMacroSeriesForDeposit() {
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.DEPOSIT, "TP.TRYTAS.MT06");
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 3, 1);
        MacroIndicator indicator = mock(MacroIndicator.class);
        MacroIndicatorPoint p1 = mock(MacroIndicatorPoint.class);
        MacroIndicatorPoint p2 = mock(MacroIndicatorPoint.class);
        when(p1.getObservedAt()).thenReturn(LocalDate.of(2024, 1, 4));
        when(p1.getValue()).thenReturn(new BigDecimal("45.20"));
        when(p2.getObservedAt()).thenReturn(LocalDate.of(2024, 2, 1));
        when(p2.getValue()).thenReturn(new BigDecimal("47.10"));
        when(macroQueryService.findByCode("TP.TRYTAS.MT06")).thenReturn(indicator);
        when(macroQueryService.history(eq(indicator), eq(from), eq(to))).thenReturn(List.of(p1, p2));

        List<HistoryPoint> result = service.getSeries(instrument, from, to);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).value()).isEqualByComparingTo("45.20");
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2024, 2, 1));
    }

    @Test
    void shouldReturnEmptyWhenMacroLookupFails() {
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.DEPOSIT, "MISSING");
        when(macroQueryService.findByCode("MISSING")).thenThrow(new RuntimeException("not found"));

        List<HistoryPoint> result = service.getSeries(instrument, LocalDate.now().minusDays(30), LocalDate.now());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldMemoizeRepeatedSeriesFetchForSameWindow() {
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "THYAO.IS");
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(historicalPricingPort.getPriceSeries(MarketType.STOCK, "THYAO.IS", from, to))
                .thenReturn(Map.of(LocalDate.of(2024, 3, 1), new BigDecimal("200")));

        service.getSeries(instrument, from, to);
        service.getSeries(instrument, from, to);

        verify(historicalPricingPort, times(1)).getPriceSeries(MarketType.STOCK, "THYAO.IS", from, to);
    }

    @Test
    void shouldNotCacheEmptySeriesSoTransientFailuresRetry() {
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "THYAO.IS");
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(historicalPricingPort.getPriceSeries(MarketType.STOCK, "THYAO.IS", from, to)).thenReturn(Map.of());

        service.getSeries(instrument, from, to);
        service.getSeries(instrument, from, to);

        verify(historicalPricingPort, times(2)).getPriceSeries(MarketType.STOCK, "THYAO.IS", from, to);
    }

    @Test
    void shouldConvertCryptoMapToList() {
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.CRYPTO, "bitcoin");
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 2, 1);
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        series.put(LocalDate.of(2024, 1, 5), new BigDecimal("1500000"));
        series.put(LocalDate.of(2024, 1, 25), new BigDecimal("1620000"));
        when(historicalPricingPort.getPriceSeries(MarketType.CRYPTO, "bitcoin", from, to)).thenReturn(series);

        List<HistoryPoint> result = service.getSeries(instrument, from, to);

        assertThat(result).extracting(HistoryPoint::value)
                .containsExactly(new BigDecimal("1500000"), new BigDecimal("1620000"));
    }
}
