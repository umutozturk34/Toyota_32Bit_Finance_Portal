package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.dto.response.ScenarioSeries;
import com.finance.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceTest {

    @Mock private UnifiedHistoryService historyService;

    @InjectMocks
    private ScenarioService service;

    @Test
    void shouldComputeNominalAndRealReturnForMarketAsset() {
        AnalyticsInstrument spot = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "THYAO.IS");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 12, 31);
        when(historyService.getSeries(spot, start, end)).thenReturn(List.of(
                new HistoryPoint(LocalDate.of(2024, 1, 5), new BigDecimal("200")),
                new HistoryPoint(LocalDate.of(2024, 12, 20), new BigDecimal("300"))
        ));
        when(historyService.getMacroSeries(anyString(), any(), any())).thenReturn(List.of(
                new HistoryPoint(LocalDate.of(2023, 12, 1), new BigDecimal("2000")),
                new HistoryPoint(LocalDate.of(2024, 12, 1), new BigDecimal("2400"))
        ));

        ScenarioResponse response = service.simulate(new ScenarioRequest(
                new BigDecimal("10000"), start, end, List.of(spot)));

        ScenarioSeries series = response.series().get(0);
        assertThat(series.finalValue()).isEqualByComparingTo("15000.000000");
        assertThat(series.nominalReturnPct()).isEqualByComparingTo("50.0000");
        assertThat(series.realReturnPct()).isEqualByComparingTo("25.0000");
        assertThat(response.cpiGrowthPct()).isEqualByComparingTo("20.0000");
    }

    @Test
    void shouldCompoundDepositRateOverTime() {
        AnalyticsInstrument deposit = new AnalyticsInstrument(AnalyticsInstrumentType.DEPOSIT, "TP.TRYTAS.MT06");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 1);
        when(historyService.getSeries(deposit, start, end)).thenReturn(List.of(
                new HistoryPoint(LocalDate.of(2024, 1, 4), new BigDecimal("50.00"))
        ));
        when(historyService.getMacroSeries(anyString(), any(), any())).thenReturn(List.of(
                new HistoryPoint(LocalDate.of(2023, 12, 1), new BigDecimal("2000")),
                new HistoryPoint(LocalDate.of(2024, 12, 1), new BigDecimal("2400"))
        ));

        ScenarioResponse response = service.simulate(new ScenarioRequest(
                new BigDecimal("10000"), start, end, List.of(deposit)));

        ScenarioSeries series = response.series().get(0);
        assertThat(series.finalValue()).isGreaterThan(new BigDecimal("16000"));
        assertThat(series.finalValue()).isLessThan(new BigDecimal("17000"));
        assertThat(series.nominalReturnPct()).isGreaterThan(new BigDecimal("60"));
    }

    @Test
    void shouldReturnEmptySeriesWhenNoHistory() {
        AnalyticsInstrument missing = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "GHOST");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 6, 1);
        when(historyService.getSeries(missing, start, end)).thenReturn(List.of());
        when(historyService.getMacroSeries(anyString(), any(), any())).thenReturn(List.of());

        ScenarioResponse response = service.simulate(new ScenarioRequest(
                new BigDecimal("10000"), start, end, List.of(missing)));

        ScenarioSeries series = response.series().get(0);
        assertThat(series.points()).isEmpty();
        assertThat(series.finalValue()).isNull();
        assertThat(series.partial()).isTrue();
    }

    @Test
    void shouldRejectInvalidDateRange() {
        AnalyticsInstrument spot = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "ANY");
        LocalDate start = LocalDate.of(2024, 6, 1);
        LocalDate end = LocalDate.of(2024, 1, 1);

        assertThatThrownBy(() -> service.simulate(new ScenarioRequest(
                new BigDecimal("10000"), start, end, List.of(spot))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.analytics.invalidDateRange");
    }

    @Test
    void shouldReturnNullRealReturnWhenCpiUnavailable() {
        AnalyticsInstrument spot = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "ASELS.IS");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 12, 31);
        when(historyService.getSeries(spot, start, end)).thenReturn(List.of(
                new HistoryPoint(LocalDate.of(2024, 1, 5), new BigDecimal("100")),
                new HistoryPoint(LocalDate.of(2024, 12, 20), new BigDecimal("150"))
        ));
        when(historyService.getMacroSeries(anyString(), any(), any())).thenReturn(List.of());

        ScenarioResponse response = service.simulate(new ScenarioRequest(
                new BigDecimal("10000"), start, end, List.of(spot)));

        ScenarioSeries series = response.series().get(0);
        assertThat(series.nominalReturnPct()).isEqualByComparingTo("50.0000");
        assertThat(series.realReturnPct()).isNull();
        assertThat(response.cpiGrowthPct()).isNull();
    }

    @Test
    void shouldComputeAllInstrumentsInParallel() {
        AnalyticsInstrument spot = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "A");
        AnalyticsInstrument forex = new AnalyticsInstrument(AnalyticsInstrumentType.FOREX, "USD");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 12, 31);
        when(historyService.getSeries(spot, start, end)).thenReturn(List.of(
                new HistoryPoint(LocalDate.of(2024, 1, 5), new BigDecimal("100")),
                new HistoryPoint(LocalDate.of(2024, 12, 20), new BigDecimal("200"))));
        when(historyService.getSeries(forex, start, end)).thenReturn(List.of(
                new HistoryPoint(LocalDate.of(2024, 1, 5), new BigDecimal("30")),
                new HistoryPoint(LocalDate.of(2024, 12, 20), new BigDecimal("36"))));
        when(historyService.getMacroSeries(anyString(), any(), any())).thenReturn(List.of(
                new HistoryPoint(LocalDate.of(2023, 12, 1), new BigDecimal("2000")),
                new HistoryPoint(LocalDate.of(2024, 12, 1), new BigDecimal("2400"))));

        ScenarioResponse response = service.simulate(new ScenarioRequest(
                new BigDecimal("10000"), start, end, List.of(spot, forex)));

        assertThat(response.series()).hasSize(2);
        assertThat(response.series().get(0).nominalReturnPct()).isEqualByComparingTo("100.0000");
        assertThat(response.series().get(1).nominalReturnPct()).isEqualByComparingTo("20.0000");
    }
}
