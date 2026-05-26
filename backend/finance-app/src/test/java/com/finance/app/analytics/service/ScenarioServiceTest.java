package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.dto.response.ScenarioSeries;
import com.finance.app.config.AnalyticsProperties;
import com.finance.common.exception.BadRequestException;
import com.finance.common.model.Currency;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScenarioServiceTest {

    @Mock private UnifiedHistoryService historyService;
    @Mock private MacroIndicatorQueryService macroQueryService;
    @Mock private AnalyticsPriceSeriesProvider priceSeriesProvider;
    @Mock private AnalyticsProperties analyticsProperties;

    private ScenarioService service;

    @BeforeEach
    void setUp() {
        when(analyticsProperties.scenario()).thenReturn(new AnalyticsProperties.Scenario(7));
        service = new ScenarioService(historyService, macroQueryService, priceSeriesProvider, analyticsProperties);
    }

    private static PricedSeries pricedTry(List<HistoryPoint> points) {
        Map<LocalDate, BigDecimal> fx = new HashMap<>();
        for (HistoryPoint p : points) fx.put(p.date(), BigDecimal.ONE);
        return new PricedSeries(points, Currency.TRY, Currency.TRY, BigDecimal.ONE, fx);
    }

    @Test
    void shouldComputeNominalAndRealReturnForMarketAsset() {
        AnalyticsInstrument spot = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "THYAO.IS");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 12, 31);
        when(priceSeriesProvider.fetch(eq(spot), eq(start), eq(end), any())).thenReturn(pricedTry(List.of(
                new HistoryPoint(LocalDate.of(2024, 1, 5), new BigDecimal("200")),
                new HistoryPoint(LocalDate.of(2024, 12, 20), new BigDecimal("300"))
        )));
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
        Map<LocalDate, BigDecimal> fx = new HashMap<>();
        fx.put(LocalDate.of(2024, 1, 4), BigDecimal.ONE);
        fx.put(LocalDate.of(2024, 12, 31), BigDecimal.ONE);
        fx.put(end, BigDecimal.ONE);
        PricedSeries depositSeries = new PricedSeries(
                List.of(
                        new HistoryPoint(LocalDate.of(2024, 1, 4), new BigDecimal("50.00")),
                        new HistoryPoint(LocalDate.of(2024, 12, 31), new BigDecimal("50.00"))
                ),
                Currency.TRY, Currency.TRY, BigDecimal.ONE, fx);
        when(priceSeriesProvider.fetch(eq(deposit), eq(start), eq(end), any())).thenReturn(depositSeries);
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
        when(priceSeriesProvider.fetch(eq(missing), eq(start), eq(end), any())).thenReturn(pricedTry(List.of()));
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
        when(priceSeriesProvider.fetch(eq(spot), eq(start), eq(end), any())).thenReturn(pricedTry(List.of(
                new HistoryPoint(LocalDate.of(2024, 1, 5), new BigDecimal("100")),
                new HistoryPoint(LocalDate.of(2024, 12, 20), new BigDecimal("150"))
        )));
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
        when(priceSeriesProvider.fetch(eq(spot), eq(start), eq(end), any())).thenReturn(pricedTry(List.of(
                new HistoryPoint(LocalDate.of(2024, 1, 5), new BigDecimal("100")),
                new HistoryPoint(LocalDate.of(2024, 12, 20), new BigDecimal("200")))));
        when(priceSeriesProvider.fetch(eq(forex), eq(start), eq(end), any())).thenReturn(pricedTry(List.of(
                new HistoryPoint(LocalDate.of(2024, 1, 5), new BigDecimal("30")),
                new HistoryPoint(LocalDate.of(2024, 12, 20), new BigDecimal("36")))));
        when(historyService.getMacroSeries(anyString(), any(), any())).thenReturn(List.of(
                new HistoryPoint(LocalDate.of(2023, 12, 1), new BigDecimal("2000")),
                new HistoryPoint(LocalDate.of(2024, 12, 1), new BigDecimal("2400"))));

        ScenarioResponse response = service.simulate(new ScenarioRequest(
                new BigDecimal("10000"), start, end, List.of(spot, forex)));

        assertThat(response.series()).hasSize(2);
        assertThat(response.series().get(0).nominalReturnPct()).isEqualByComparingTo("100.0000");
        assertThat(response.series().get(1).nominalReturnPct()).isEqualByComparingTo("20.0000");
    }

    @Test
    void shouldKeepUsdValueFlatWhenHoldingForexUsdInUsdFrame() {
        AnalyticsInstrument forex = new AnalyticsInstrument(AnalyticsInstrumentType.FOREX, "USD");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 12, 31);
        LocalDate p1 = LocalDate.of(2024, 1, 5);
        LocalDate p2 = LocalDate.of(2024, 12, 20);
        Map<LocalDate, BigDecimal> fx = new HashMap<>();
        fx.put(p1, new BigDecimal("0.0333333333"));
        fx.put(p2, new BigDecimal("0.0222222222"));
        PricedSeries forexSeries = new PricedSeries(
                List.of(new HistoryPoint(p1, new BigDecimal("30")),
                        new HistoryPoint(p2, new BigDecimal("45"))),
                Currency.TRY, Currency.USD, new BigDecimal("0.0333333333"), fx);
        when(priceSeriesProvider.fetch(eq(forex), eq(start), eq(end), eq(Currency.USD))).thenReturn(forexSeries);

        ScenarioResponse response = service.simulate(new ScenarioRequest(
                new BigDecimal("100000"), start, end, List.of(forex), Currency.USD));

        ScenarioSeries series = response.series().get(0);
        assertThat(series.points().get(0).value().doubleValue()).isCloseTo(100_000.0, within(1.0));
        assertThat(series.points().get(1).value().doubleValue()).isCloseTo(100_000.0, within(1.0));
        assertThat(series.nominalReturnPct().doubleValue()).isCloseTo(0.0, within(0.01));
    }

    @Test
    void shouldConvertMarketSeriesWhenTargetCurrencyDiffersFromNative() {
        AnalyticsInstrument crypto = new AnalyticsInstrument(AnalyticsInstrumentType.CRYPTO, "bitcoin");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 12, 31);
        LocalDate p1 = LocalDate.of(2024, 1, 5);
        LocalDate p2 = LocalDate.of(2024, 12, 20);
        Map<LocalDate, BigDecimal> fx = new HashMap<>();
        fx.put(p1, new BigDecimal("0.9"));
        fx.put(p2, new BigDecimal("0.85"));
        PricedSeries btcSeries = new PricedSeries(
                List.of(new HistoryPoint(p1, new BigDecimal("40000")),
                        new HistoryPoint(p2, new BigDecimal("80000"))),
                Currency.USD, Currency.EUR, new BigDecimal("0.9"), fx);
        when(priceSeriesProvider.fetch(eq(crypto), eq(start), eq(end), eq(Currency.EUR))).thenReturn(btcSeries);

        ScenarioResponse response = service.simulate(new ScenarioRequest(
                new BigDecimal("10000"), start, end, List.of(crypto), Currency.EUR));

        ScenarioSeries series = response.series().get(0);
        assertThat(series.points().get(0).value()).isEqualByComparingTo("10000.000000");
        assertThat(series.points().get(1).value()).isEqualByComparingTo("18888.888889");
        assertThat(response.cpiGrowthPct()).isNull();
    }
}
