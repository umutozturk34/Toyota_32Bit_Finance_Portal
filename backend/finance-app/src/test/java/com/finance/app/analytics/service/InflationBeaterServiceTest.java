package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.app.analytics.dto.response.ScenarioPoint;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.dto.response.ScenarioSeries;
import com.finance.common.exception.BadRequestException;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InflationBeaterServiceTest {

    @Mock private ScenarioService scenarioService;
    @Mock private UnifiedHistoryService historyService;
    @Mock private MacroIndicatorQueryService macroQueryService;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;

    @InjectMocks
    private InflationBeaterService service;

    @BeforeEach
    void wireTrackedDefaults() {
        for (TrackedAssetType t : TrackedAssetType.values()) {
            lenient().when(trackedAssetQueryService.getCodes(t)).thenReturn(List.of());
            lenient().when(trackedAssetQueryService.getDisplayNameMap(t)).thenReturn(Map.of());
        }
    }

    @Test
    void shouldRankEntriesByRealReturnDescending() {
        wireInflationBenchmark(new BigDecimal("25"));
        ScenarioSeries highReal = buildSeries(AnalyticsInstrumentType.SPOT, "A", new BigDecimal("100"));
        ScenarioSeries midReal = buildSeries(AnalyticsInstrumentType.SPOT, "B", new BigDecimal("50"));
        ScenarioSeries lowReal = buildSeries(AnalyticsInstrumentType.SPOT, "C", new BigDecimal("10"));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                new BigDecimal("25"), null, List.of(lowReal, highReal, midReal)));

        InflationBeaterResponse response = service.rank("1Y", null);

        assertThat(response.entries()).hasSize(3);
        assertThat(response.entries().get(0).excessReturnPct()).isEqualByComparingTo("75");
        assertThat(response.entries().get(1).excessReturnPct()).isEqualByComparingTo("25");
        assertThat(response.entries().get(2).excessReturnPct()).isEqualByComparingTo("-15");
    }

    @Test
    void shouldCountAssetsThatBeatBenchmark() {
        wireInflationBenchmark(new BigDecimal("25"));
        ScenarioSeries beater1 = buildSeries(AnalyticsInstrumentType.SPOT, "X", new BigDecimal("80"));
        ScenarioSeries beater2 = buildSeries(AnalyticsInstrumentType.FOREX, "USD", new BigDecimal("40"));
        ScenarioSeries loser = buildSeries(AnalyticsInstrumentType.SPOT, "Z", new BigDecimal("5"));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                new BigDecimal("25"), null, List.of(beater1, loser, beater2)));

        InflationBeaterResponse response = service.rank("1Y", null);

        assertThat(response.beatingCount()).isEqualTo(2);
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.benchmarkReturnPct()).isEqualByComparingTo("25");
    }

    @Test
    void shouldUseRequestedPeriodWindow() {
        wireInflationBenchmark(new BigDecimal("12"));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusMonths(6), LocalDate.now(),
                BigDecimal.ZERO, null, List.of()));

        service.rank("6M", null);

        ArgumentCaptor<ScenarioRequest> captor = ArgumentCaptor.forClass(ScenarioRequest.class);
        verify(scenarioService).simulate(captor.capture());
        long monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(
                captor.getValue().startDate(), captor.getValue().endDate());
        assertThat(monthsBetween).isEqualTo(6);
    }

    @Test
    void shouldUseRateBenchmarkFromScenarioSeries() {
        MacroIndicator policyRate = mock(MacroIndicator.class);
        when(policyRate.getCategory()).thenReturn(MacroCategory.RATES);
        when(policyRate.getUnit()).thenReturn(MacroUnit.PERCENT);
        lenient().when(policyRate.getLabel()).thenReturn("policyRate");
        when(macroQueryService.findByCode("TP.POLICY")).thenReturn(policyRate);

        ScenarioSeries asset = buildSeries(AnalyticsInstrumentType.SPOT, "A", new BigDecimal("60"));
        ScenarioSeries benchmark = buildSeries(AnalyticsInstrumentType.MACRO, "TP.POLICY", new BigDecimal("45"));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                null, null, List.of(asset, benchmark)));

        InflationBeaterResponse response = service.rank("1Y", "TP.POLICY");

        assertThat(response.benchmarkReturnPct()).isEqualByComparingTo("45");
        assertThat(response.entries()).hasSize(1);
        assertThat(response.entries().get(0).excessReturnPct()).isEqualByComparingTo("15");
    }

    @Test
    void shouldRejectUnknownPeriod() {
        assertThatThrownBy(() -> service.rank("invalid", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.analytics.unknownPeriod");
    }

    @Test
    void shouldDeriveCurrencyFromBenchmarkUsingResolver() {
        com.finance.market.core.service.AssetNativeCurrencyResolver resolver =
                mock(com.finance.market.core.service.AssetNativeCurrencyResolver.class);
        when(resolver.resolveNativeCurrency(any(), org.mockito.ArgumentMatchers.eq("TP.USDTAS.MT06")))
                .thenReturn(com.finance.common.model.Currency.USD);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "nativeCurrencyResolver", resolver);

        MacroIndicator usdDeposit = mock(MacroIndicator.class);
        lenient().when(usdDeposit.getCategory()).thenReturn(MacroCategory.DEPOSIT);
        lenient().when(usdDeposit.getUnit()).thenReturn(MacroUnit.PERCENT);
        lenient().when(usdDeposit.getLabel()).thenReturn("usdDeposit");
        when(macroQueryService.findByCode("TP.USDTAS.MT06")).thenReturn(usdDeposit);
        ScenarioSeries asset = buildSeries(AnalyticsInstrumentType.SPOT, "A", new BigDecimal("20"));
        ScenarioSeries benchmark = buildSeries(AnalyticsInstrumentType.DEPOSIT, "TP.USDTAS.MT06", new BigDecimal("10"));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                null, null, List.of(asset, benchmark)));

        InflationBeaterResponse response = service.rank("1Y", "TP.USDTAS.MT06");

        ArgumentCaptor<ScenarioRequest> captor = ArgumentCaptor.forClass(ScenarioRequest.class);
        verify(scenarioService).simulate(captor.capture());
        assertThat(captor.getValue().targetCurrency()).isEqualTo(com.finance.common.model.Currency.USD);
        assertThat(response.comparisonCurrency()).isEqualTo(com.finance.common.model.Currency.USD);
    }

    private void wireInflationBenchmark(BigDecimal growthPct) {
        MacroIndicator cpi = mock(MacroIndicator.class);
        lenient().when(cpi.getCategory()).thenReturn(MacroCategory.INFLATION);
        lenient().when(cpi.getUnit()).thenReturn(MacroUnit.INDEX);
        lenient().when(cpi.getLabel()).thenReturn("cpiIndex");
        when(macroQueryService.findByCode(anyString())).thenReturn(cpi);
        BigDecimal base = new BigDecimal("1000");
        BigDecimal end = base.add(base.multiply(growthPct).divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP));
        when(historyService.getMacroSeries(anyString(), any(), any())).thenReturn(List.of(
                new HistoryPoint(LocalDate.now().minusYears(1).minusMonths(2), base),
                new HistoryPoint(LocalDate.now(), end)
        ));
    }

    private ScenarioSeries buildSeries(AnalyticsInstrumentType type, String code, BigDecimal nominalPct) {
        return new ScenarioSeries(
                new AnalyticsInstrument(type, code),
                List.of(new ScenarioPoint(LocalDate.now(), new BigDecimal("10000"))),
                new BigDecimal("11000"), nominalPct, null, null, false);
    }
}
