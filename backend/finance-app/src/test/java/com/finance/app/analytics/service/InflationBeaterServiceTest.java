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
import com.finance.market.macro.model.DepositMaturity;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

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
    @Spy private BeaterCacheManager cacheManager = new BeaterCacheManager();
    @Mock private ObjectProvider<com.finance.market.core.service.AssetNativeCurrencyResolver> nativeCurrencyResolver;
    @Mock private ObjectProvider<com.finance.app.config.MarketDataInitializer> marketDataInitializer;

    private InflationBeaterService service;

    @BeforeEach
    void wireTrackedDefaults() {
        // Built by hand rather than @InjectMocks: both cold-start guards are erased-type ObjectProvider, which
        // constructor injection can't disambiguate. Each getIfAvailable() defaults to null — treated as ready
        // and TRY-based — until a test stubs it.
        service = new InflationBeaterService(scenarioService, historyService, macroQueryService,
                trackedAssetQueryService, cacheManager, nativeCurrencyResolver, marketDataInitializer);
        for (TrackedAssetType t : TrackedAssetType.values()) {
            lenient().when(trackedAssetQueryService.getCodes(t)).thenReturn(List.of());
            lenient().when(trackedAssetQueryService.getEnabledCodes(t)).thenReturn(List.of());
            lenient().when(trackedAssetQueryService.getDisplayNameMap(t)).thenReturn(Map.of());
        }
    }

    @Test
    void shouldReturnEmptyRanking_whenMarketDataStillInitializing() {
        // Arrange — cold-start init not finished (completion future never completes). rank must short-circuit:
        // no compute (findByCode is unstubbed → would NPE), no scenario simulation, just an empty ranking.
        com.finance.app.config.MarketDataInitializer initializer =
                mock(com.finance.app.config.MarketDataInitializer.class);
        when(initializer.completion()).thenReturn(new java.util.concurrent.CompletableFuture<>());
        when(marketDataInitializer.getIfAvailable()).thenReturn(initializer);

        // Act
        InflationBeaterResponse response = service.rank("1Y", null);

        // Assert
        assertThat(response.entries()).isEmpty();
        assertThat(response.totalCount()).isZero();
        verify(scenarioService, org.mockito.Mockito.never()).simulate(any());
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
        // Geometric (Fisher) excess vs CPI +25%: A +100% -> (2.00/1.25-1)=60; B +50% -> (1.50/1.25-1)=20;
        // C +10% -> (1.10/1.25-1)=-12. DESC order and beats/loses sign are identical to the old naive form.
        assertThat(response.entries().get(0).excessReturnPct()).isEqualByComparingTo("60");
        assertThat(response.entries().get(1).excessReturnPct()).isEqualByComparingTo("20");
        assertThat(response.entries().get(2).excessReturnPct()).isEqualByComparingTo("-12");
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
    void shouldSimulateUniverseOncePerPeriodCurrencyAcrossBenchmarks() {
        // Two different benchmarks over the same period+currency must share ONE universe simulation
        // (the structural win): the second rank reuses the cached universe instead of re-simulating it.
        wireInflationBenchmark(new BigDecimal("20"));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                new BigDecimal("20"), null,
                List.of(buildSeries(AnalyticsInstrumentType.SPOT, "A", new BigDecimal("50")))));

        service.rank("1Y", "TP.GENENDEKS.T1");
        service.rank("1Y", "TP.TUFE1YI.T1");

        verify(scenarioService).simulate(any());
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
    void shouldEndWindowToday_notBenchmarkLastObservation() {
        // Arrange — the window must run to today so the most recent days of accrued interest / price moves
        // count, instead of anchoring to a lagging benchmark print.
        wireInflationBenchmark(new BigDecimal("10"));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                BigDecimal.ZERO, null, List.of()));

        // Act
        service.rank("1Y", null);

        // Assert
        ArgumentCaptor<ScenarioRequest> captor = ArgumentCaptor.forClass(ScenarioRequest.class);
        verify(scenarioService).simulate(captor.capture());
        assertThat(captor.getValue().endDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void shouldUseRateBenchmarkFromScenarioSeries() {
        MacroIndicator policyRate = mock(MacroIndicator.class);
        // getCategory is only consulted on the RATES "simulate-alone" path; here the benchmark is present in
        // the mocked universe series, so its return is read straight from there — make the stub optional.
        lenient().when(policyRate.getCategory()).thenReturn(MacroCategory.RATES);
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
        // Geometric excess: asset +60% vs policy-rate benchmark +45% -> (1.60/1.45-1) = 10.3448%.
        assertThat(response.entries().get(0).excessReturnPct()).isEqualByComparingTo("10.3448");
    }

    @Test
    void shouldResolveBenchmarkByPublicSlug() {
        // Regression: the API sends the EVDS-free public slug ("cpiindex"), not the raw EVDS code. The service
        // must canonicalise it to the EVDS-coded indicator and compute — it used to 404 because compute() only
        // knew findByCode (EVDS column). The heavy compute/cache must still key on the canonical EVDS code so a
        // slug request hits the warm cache the scheduler filled, and the response carries the slug back.
        MacroIndicator cpi = mock(MacroIndicator.class);
        lenient().when(cpi.getCategory()).thenReturn(MacroCategory.INFLATION);
        lenient().when(cpi.getUnit()).thenReturn(MacroUnit.INDEX);
        lenient().when(cpi.getLabel()).thenReturn("cpiIndex");
        lenient().when(cpi.getCode()).thenReturn("TP.GENENDEKS.T1");
        lenient().when(cpi.getSlug()).thenReturn("cpiindex");
        when(macroQueryService.findByPublicId("cpiindex")).thenReturn(cpi);
        when(macroQueryService.findByCode("TP.GENENDEKS.T1")).thenReturn(cpi);
        when(historyService.getMacroSeries(anyString(), any(), any())).thenReturn(List.of(
                new HistoryPoint(LocalDate.now().minusYears(1), new BigDecimal("1000")),
                new HistoryPoint(LocalDate.now(), new BigDecimal("1250"))));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                new BigDecimal("25"), null,
                List.of(buildSeries(AnalyticsInstrumentType.SPOT, "A", new BigDecimal("100")))));

        InflationBeaterResponse response = service.rank("1Y", "cpiindex");

        assertThat(response.entries()).isNotEmpty();
        assertThat(response.benchmarkCode()).isEqualTo("cpiindex");
        verify(macroQueryService).findByCode("TP.GENENDEKS.T1");
    }

    @Test
    void shouldComputeUsdBasisReturnWhenBenchmarkIsUsdDeposit() {
        com.finance.market.core.service.AssetNativeCurrencyResolver resolver =
                mock(com.finance.market.core.service.AssetNativeCurrencyResolver.class);
        when(resolver.resolveNativeCurrency(any(), org.mockito.ArgumentMatchers.eq("TP.USDTAS.MT03")))
                .thenReturn(com.finance.common.model.Currency.USD);
        when(nativeCurrencyResolver.getIfAvailable()).thenReturn(resolver);

        MacroIndicator usdDeposit = mock(MacroIndicator.class);
        lenient().when(usdDeposit.getCategory()).thenReturn(MacroCategory.DEPOSIT);
        lenient().when(usdDeposit.getUnit()).thenReturn(MacroUnit.PERCENT);
        lenient().when(usdDeposit.getLabel()).thenReturn("usdDeposit3m");
        when(macroQueryService.findByCode("TP.USDTAS.MT03")).thenReturn(usdDeposit);

        ScenarioSeries stockFlatInUsd = buildSeries(AnalyticsInstrumentType.SPOT, "BIST.XYZ", BigDecimal.ZERO);
        ScenarioSeries usdDepositReturn = buildSeries(
                AnalyticsInstrumentType.DEPOSIT, "TP.USDTAS.MT03", new BigDecimal("5"));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                null, com.finance.common.model.Currency.USD, List.of(stockFlatInUsd, usdDepositReturn)));

        InflationBeaterResponse response = service.rank("1Y", "TP.USDTAS.MT03");

        ArgumentCaptor<ScenarioRequest> captor = ArgumentCaptor.forClass(ScenarioRequest.class);
        verify(scenarioService).simulate(captor.capture());
        assertThat(captor.getValue().targetCurrency()).isEqualTo(com.finance.common.model.Currency.USD);
        assertThat(response.comparisonCurrency()).isEqualTo(com.finance.common.model.Currency.USD);
        assertThat(response.benchmarkReturnPct()).isEqualByComparingTo("5");

        assertThat(response.entries()).hasSize(1);
        assertThat(response.entries().get(0).code()).isEqualTo("BIST.XYZ");
        assertThat(response.entries().get(0).nominalReturnPct()).isEqualByComparingTo("0");
        // Geometric excess: flat-in-USD asset 0% vs USD deposit +5% -> (1.00/1.05-1) = -4.7619%.
        assertThat(response.entries().get(0).excessReturnPct()).isEqualByComparingTo("-4.7619");
        assertThat(response.entries().get(0).beatsBenchmark()).isFalse();
    }

    @Test
    void shouldForceTryComparisonForIndexBenchmarkEvenWithNonTryOverride() {
        // A CPI (INDEX) benchmark's return is raw TRY-basis index growth (computeIndexGrowthPct, no FX), so
        // a non-TRY override must NOT frame the universe in USD/EUR — otherwise a USD nominal is compared
        // against a ~TRY CPI and nearly everything is wrongly flagged as not beating inflation. The override
        // is honored only for rate-backed benchmarks; an index benchmark forces a TRY comparison.
        wireInflationBenchmark(new BigDecimal("30"));
        ScenarioSeries asset = buildSeries(AnalyticsInstrumentType.SPOT, "A", new BigDecimal("40"));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                new BigDecimal("30"), com.finance.common.model.Currency.TRY, List.of(asset)));

        InflationBeaterResponse response = service.rank("1Y", null, "USD");

        ArgumentCaptor<ScenarioRequest> captor = ArgumentCaptor.forClass(ScenarioRequest.class);
        verify(scenarioService).simulate(captor.capture());
        assertThat(captor.getValue().targetCurrency()).isEqualTo(com.finance.common.model.Currency.TRY);
        assertThat(response.comparisonCurrency()).isEqualTo(com.finance.common.model.Currency.TRY);
        assertThat(response.benchmarkReturnPct()).isEqualByComparingTo("30");
        // Geometric excess: asset +40% vs CPI +30% -> (1.40/1.30-1) = 7.6923%.
        assertThat(response.entries().get(0).excessReturnPct()).isEqualByComparingTo("7.6923");
        assertThat(response.entries().get(0).beatsBenchmark()).isTrue();
    }

    @Test
    void shouldMatchCompareScenarioReturnInBeater() {
        wireInflationBenchmark(new BigDecimal("25"));
        BigDecimal fundReturn = new BigDecimal("48.86");
        ScenarioSeries pteSeries = buildSeries(AnalyticsInstrumentType.FUND, "PTE", fundReturn);
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                new BigDecimal("25"), null, List.of(pteSeries)));

        InflationBeaterResponse response = service.rank("1Y", null);

        ArgumentCaptor<ScenarioRequest> captor = ArgumentCaptor.forClass(ScenarioRequest.class);
        verify(scenarioService).simulate(captor.capture());
        ScenarioRequest beaterRequest = captor.getValue();

        ScenarioResponse direct = new ScenarioResponse(
                beaterRequest.amount(), beaterRequest.startDate(), beaterRequest.endDate(),
                new BigDecimal("25"), beaterRequest.targetCurrency(), List.of(pteSeries));
        BigDecimal directReturn = direct.series().get(0).nominalReturnPct();

        assertThat(response.entries()).hasSize(1);
        assertThat(response.entries().get(0).nominalReturnPct()).isEqualByComparingTo(directReturn);
        assertThat(response.entries().get(0).code()).isEqualTo("PTE");
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
        when(nativeCurrencyResolver.getIfAvailable()).thenReturn(resolver);

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

    @Test
    void shouldEnumerateDepositUniverseFromCatalogExcludingTotalBucket() {
        wireInflationBenchmark(new BigDecimal("20"));
        MacroIndicator try3m = depositIndicator("TP.TRYTAS.MT02", "TRY", DepositMaturity.M3);
        MacroIndicator eur1y = depositIndicator("TP.EURTAS.MT04", "EUR", DepositMaturity.M12);
        MacroIndicator usdTotal = depositIndicator("TP.USDTAS.MT06", "USD", DepositMaturity.TOTAL);
        when(macroQueryService.listByCategory(MacroCategory.DEPOSIT))
                .thenReturn(List.of(try3m, eur1y, usdTotal));
        when(scenarioService.simulate(any())).thenReturn(new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.now().minusYears(1), LocalDate.now(),
                new BigDecimal("20"), null, List.of()));

        service.rank("1Y", null);

        ArgumentCaptor<ScenarioRequest> captor = ArgumentCaptor.forClass(ScenarioRequest.class);
        verify(scenarioService).simulate(captor.capture());
        List<String> codes = captor.getValue().instruments().stream()
                .map(AnalyticsInstrument::code).toList();
        assertThat(codes).contains("TP.TRYTAS.MT02", "TP.EURTAS.MT04");
        assertThat(codes).doesNotContain("TP.USDTAS.MT06");
    }

    private MacroIndicator depositIndicator(String code, String currency, DepositMaturity maturity) {
        MacroIndicator d = mock(MacroIndicator.class);
        lenient().when(d.getCode()).thenReturn(code);
        lenient().when(d.getCurrency()).thenReturn(currency);
        lenient().when(d.getMaturity()).thenReturn(maturity);
        return d;
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

    @Test
    void shouldEnumerateEverySelectableBenchmark_acrossAllCategories() {
        // Arrange — build mocks first; a nested when() inside when(...).thenReturn(...) is illegal stubbing.
        MacroIndicator cpi = macroWithCode("TP.GENENDEKS.T1");
        MacroIndicator policy = macroWithCode("TP.POLICY");
        MacroIndicator depTry = macroWithCode("TP.TRYTAS.MT02");
        MacroIndicator depUsd = macroWithCode("TP.USDTAS.MT06");
        when(macroQueryService.listByCategory(MacroCategory.INFLATION)).thenReturn(List.of(cpi));
        when(macroQueryService.listByCategory(MacroCategory.RATES)).thenReturn(List.of(policy));
        when(macroQueryService.listByCategory(MacroCategory.DEPOSIT)).thenReturn(List.of(depTry, depUsd));

        // Act
        List<String> codes = service.warmableBenchmarkCodes();

        // Assert
        assertThat(codes).containsExactly("TP.GENENDEKS.T1", "TP.POLICY", "TP.TRYTAS.MT02", "TP.USDTAS.MT06");
    }

    @Test
    void shouldSkipFailingCategory_whenEnumeratingBenchmarks() {
        // Arrange — one category throws; the others must still enumerate. Build mocks first (see above).
        MacroIndicator cpi = macroWithCode("TP.GENENDEKS.T1");
        MacroIndicator depTry = macroWithCode("TP.TRYTAS.MT02");
        when(macroQueryService.listByCategory(MacroCategory.INFLATION)).thenReturn(List.of(cpi));
        when(macroQueryService.listByCategory(MacroCategory.RATES))
                .thenThrow(new RuntimeException("catalog down"));
        when(macroQueryService.listByCategory(MacroCategory.DEPOSIT)).thenReturn(List.of(depTry));

        // Act
        List<String> codes = service.warmableBenchmarkCodes();

        // Assert
        assertThat(codes).containsExactly("TP.GENENDEKS.T1", "TP.TRYTAS.MT02");
    }

    @Test
    void shouldExposeEverySupportedPeriod_forWarmup() {
        // Act + Assert
        assertThat(service.warmablePeriods())
                .containsExactlyInAnyOrder("1M", "3M", "6M", "1Y", "3Y", "5Y");
    }

    private MacroIndicator macroWithCode(String code) {
        MacroIndicator m = mock(MacroIndicator.class);
        when(m.getCode()).thenReturn(code);
        return m;
    }
}
