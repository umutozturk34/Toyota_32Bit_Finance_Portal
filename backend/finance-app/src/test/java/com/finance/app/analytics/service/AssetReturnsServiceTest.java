package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.RiskLevel;
import com.finance.app.analytics.dto.response.AssetReturnRow;
import com.finance.app.analytics.dto.response.AssetReturnsResponse;
import com.finance.app.analytics.dto.response.PeriodReturn;
import com.finance.common.model.Currency;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.core.service.TrackedAssetQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetReturnsServiceTest {

    @Mock private UnifiedHistoryService historyService;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private CurrencyConverter currencyConverter;
    @Spy private AssetReturnsCacheManager cacheManager = new AssetReturnsCacheManager();
    @Mock private ObjectProvider<com.finance.app.config.MarketDataInitializer> marketDataInitializer;

    @InjectMocks
    private AssetReturnsService service;

    @BeforeEach
    void wireTrackedDefaults() {
        // Default every tracked type to an empty universe; individual tests enable just the codes they need.
        for (TrackedAssetType t : TrackedAssetType.values()) {
            lenient().when(trackedAssetQueryService.getEnabledCodes(t)).thenReturn(List.of());
            lenient().when(trackedAssetQueryService.getDisplayNameMap(t)).thenReturn(Map.of());
        }
    }

    @Test
    void shouldReturnEmptyDataset_whenMarketDataStillInitializing() {
        // Arrange — cold-start init not finished (completion future never completes): no series fetch at all.
        com.finance.app.config.MarketDataInitializer initializer =
                mock(com.finance.app.config.MarketDataInitializer.class);
        when(initializer.completion()).thenReturn(new java.util.concurrent.CompletableFuture<>());
        when(marketDataInitializer.getIfAvailable()).thenReturn(initializer);

        // Act
        AssetReturnsResponse response = service.getReturns();

        // Assert
        assertThat(response.assets()).isEmpty();
        verify(historyService, never()).getSeries(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "100, 200,  100.00,  100.00",
            "200, 100,  -50.00, -100.00",
            "50,   50,    0.00,    0.00",
            "100, 130,   30.00,   30.00"
    })
    void shouldComputeRealizedReturnPctAndTry_overOneMonthWindow(
            String then, String now, String expectedPct, String expectedTry) {
        // Arrange — a single stock whose price moves from `then` (one month ago) to `now` (today), all TRY.
        LocalDate today = LocalDate.now();
        wireStock("AAA", List.of(
                point(today.minusMonths(1), then),
                point(today, now)));

        // Act
        PeriodReturn r = periodOf(service.getReturns(), "AAA", "1M");

        // Assert
        assertThat(r.returnPct()).isEqualByComparingTo(expectedPct);
        assertThat(r.returnTry()).isEqualByComparingTo(expectedTry);
        assertThat(r.priceThen()).isEqualByComparingTo(then);
        assertThat(r.priceNow()).isEqualByComparingTo(now);
    }

    @Test
    void shouldExcludeBondsAndViop_fromUniverse() {
        // Arrange — only a stock is enabled.
        LocalDate today = LocalDate.now();
        wireStock("AAA", List.of(point(today.minusYears(1), "100"), point(today, "150")));

        // Act
        AssetReturnsResponse response = service.getReturns();

        // Assert — VIOP (a tracked type) is never queried; bonds are not a tracked type at all, so absent.
        verify(trackedAssetQueryService, never()).getEnabledCodes(TrackedAssetType.VIOP);
        assertThat(response.assets())
                .noneMatch(a -> a.type() == AnalyticsInstrumentType.VIOP || a.type() == AnalyticsInstrumentType.BOND);
    }

    @Test
    void shouldOmitWindowsOlderThanHistory_butKeepCoveredOnes() {
        // Arrange — a stock with only ~2 months of history.
        LocalDate today = LocalDate.now();
        wireStock("YNG", List.of(
                point(today.minusMonths(2), "100"),
                point(today.minusMonths(1), "110"),
                point(today, "120")));

        // Act
        AssetReturnRow row = rowOf(service.getReturns(), "YNG");

        // Assert — the 1Y window (history doesn't reach a year back, beyond the 30-day tolerance) is dropped;
        // the 1M window is fully covered, so it's present.
        assertThat(row.periods()).doesNotContainKey("1Y");
        assertThat(row.periods().get("1M")).isNotNull();
    }

    @Test
    void shouldComputeVolatilityAndLowRisk_forFlatSeries() {
        // Arrange — a flat price has zero volatility, so it must classify as LOW risk.
        LocalDate today = LocalDate.now();
        wireStock("FLAT", List.of(
                point(today.minusMonths(1), "100"),
                point(today.minusDays(20), "100"),
                point(today.minusDays(10), "100"),
                point(today.minusDays(5), "100"),
                point(today, "100")));

        // Act
        PeriodReturn r = periodOf(service.getReturns(), "FLAT", "1M");

        // Assert
        assertThat(r.volatility()).isEqualByComparingTo("0.00");
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void shouldSkipAsset_whenSeriesHasFewerThanTwoPoints() {
        // Arrange — a single data point can't form a return.
        LocalDate today = LocalDate.now();
        wireStock("ONE", List.of(point(today, "100")));

        // Act
        AssetReturnsResponse response = service.getReturns();

        // Assert
        assertThat(response.assets()).isEmpty();
    }

    @Test
    void shouldIncludeCrypto_usingTheReusedTryConvertedSeries() {
        // Arrange — crypto is enabled; getSeries already yields TRY (USD→TRY handled upstream, not here).
        LocalDate today = LocalDate.now();
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.CRYPTO)).thenReturn(List.of("BTC"));
        when(trackedAssetQueryService.getDisplayNameMap(TrackedAssetType.CRYPTO)).thenReturn(Map.of("BTC", "Bitcoin"));
        when(historyService.getSeries(any(AnalyticsInstrument.class), any(), any()))
                .thenReturn(List.of(point(today.minusYears(1), "1000000"), point(today, "2000000")));

        // Act
        AssetReturnRow row = rowOf(service.getReturns(), "BTC");

        // Assert
        assertThat(row.type()).isEqualTo(AnalyticsInstrumentType.CRYPTO);
        assertThat(row.name()).isEqualTo("Bitcoin");
        assertThat(row.periods().get("1Y").returnPct()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldExposeFxFiguresAsSeparateRanking_whenRatesAvailable() {
        // Arrange — a stock that DOUBLES in TRY over 1Y, but whose USD value is flat (the lira halved against
        // the dollar over the same span): TRY ranking reads +100%, USD ranking ~0% — proof each currency is its
        // own ranking, not a relabeled TRY number. EUR is left unstubbed to prove the best-effort null leg.
        LocalDate today = LocalDate.now();
        LocalDate thenDate = today.minusYears(1).plusDays(2);
        LocalDate nowDate = today.minusDays(2);
        wireStock("ISCTR", List.of(point(thenDate, "100"), point(nowDate, "200")));
        SortedMap<LocalDate, BigDecimal> usdSeries = new TreeMap<>();
        usdSeries.put(thenDate, new BigDecimal("10"));
        usdSeries.put(nowDate, new BigDecimal("10"));
        when(currencyConverter.convertSeries(any(), eq(Currency.TRY), eq(Currency.USD))).thenReturn(usdSeries);

        // Act
        PeriodReturn pr = periodOf(service.getReturns(), "ISCTR", "1Y");

        // Assert
        assertThat(pr.returnPct()).isEqualByComparingTo("100.00");
        assertThat(pr.usd()).isNotNull();
        assertThat(pr.usd().returnPct()).isEqualByComparingTo("0.00");
        assertThat(pr.eur()).isNull();
    }

    @Test
    void shouldAnchorWindowToLastDataPoint_notToday() {
        // Arrange — the last NAV is a week stale (weekend/T+1, the normal case). A today-anchored 1M would
        // baseline at the today−1M point (100 → +10%); the correct last-data-anchored 1M baselines one month
        // before the LAST point (105 → ~+4.76%), matching how official returns measure from the last priced day.
        LocalDate today = LocalDate.now();
        wireStock("ANCH", List.of(
                point(today.minusMonths(2), "105"),
                point(today.minusMonths(1), "100"),
                point(today.minusDays(7), "110")));

        // Act
        PeriodReturn pr = periodOf(service.getReturns(), "ANCH", "1M");

        // Assert — last-data-anchored (110/105), NOT today-anchored (110/100)
        assertThat(pr.returnPct().doubleValue()).isCloseTo(4.76, within(0.1));
    }

    @Test
    void shouldPinNearTotalLoss_soItNeverReadsExactlyMinus100() {
        // Arrange — a near-total loss (1 from 100000 ≈ -99.999%) rounds to -100.00 at 2dp; a still-priced asset
        // can't truly be down a full 100%, so it must read -99.99% instead of a phantom total wipeout.
        LocalDate today = LocalDate.now();
        wireStock("WIPE", List.of(
                point(today.minusMonths(13), "100000"),
                point(today, "1")));

        // Act
        PeriodReturn r = periodOf(service.getReturns(), "WIPE", "1Y");

        // Assert
        assertThat(r.returnPct()).isEqualByComparingTo("-99.99");
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private void wireStock(String code, List<HistoryPoint> series) {
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK)).thenReturn(List.of(code));
        when(trackedAssetQueryService.getDisplayNameMap(TrackedAssetType.STOCK)).thenReturn(Map.of(code, code));
        when(historyService.getSeries(any(AnalyticsInstrument.class), any(), any())).thenReturn(series);
    }

    private static HistoryPoint point(LocalDate date, String value) {
        return new HistoryPoint(date, new BigDecimal(value));
    }

    private static AssetReturnRow rowOf(AssetReturnsResponse response, String code) {
        return response.assets().stream()
                .filter(a -> a.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row for code " + code));
    }

    private static PeriodReturn periodOf(AssetReturnsResponse response, String code, String period) {
        PeriodReturn r = rowOf(response, code).periods().get(period);
        assertThat(r).as("period %s for %s", period, code).isNotNull();
        return r;
    }
}
