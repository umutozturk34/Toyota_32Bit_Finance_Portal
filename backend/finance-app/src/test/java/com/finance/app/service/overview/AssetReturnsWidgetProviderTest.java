package com.finance.app.service.overview;

import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.RiskLevel;
import com.finance.app.analytics.dto.response.AssetReturnRow;
import com.finance.app.analytics.dto.response.AssetReturnsResponse;
import com.finance.app.analytics.dto.response.PeriodReturn;
import com.finance.app.analytics.service.AssetReturnsService;
import com.finance.app.dto.response.overview.AssetReturnsData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetReturnsWidgetProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AssetReturnsService assetReturnsService;

    @InjectMocks
    private AssetReturnsWidgetProvider provider;

    private WidgetSection section;

    @BeforeEach
    void setUp() {
        section = sectionFor("{}");
    }

    private WidgetSection sectionFor(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new WidgetSection("ar-1", WidgetKind.ASSET_RETURNS, 0, node);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    void shouldRankInWidgetCurrency_whenConfigSelectsUsd() {
        // Arrange — A leads in TRY (+100 vs +50) but in USD the order FLIPS (A +5 vs B +40): A's TRY gain was
        // mostly the lira falling. A USD widget must rank B first — its own ranking off the same cached dataset.
        PeriodReturn a = new PeriodReturn(
                BigDecimal.valueOf(100), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(2), null, null,
                new PeriodReturn.CurrencyFigures(BigDecimal.valueOf(5), BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, null, null),
                null);
        PeriodReturn b = new PeriodReturn(
                BigDecimal.valueOf(50), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(2), null, null,
                new PeriodReturn.CurrencyFigures(BigDecimal.valueOf(40), BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, null, null),
                null);
        Map<String, PeriodReturn> pa = new LinkedHashMap<>();
        pa.put("1Y", a);
        Map<String, PeriodReturn> pb = new LinkedHashMap<>();
        pb.put("1Y", b);
        AssetReturnsResponse dataset = new AssetReturnsResponse(LocalDate.now(), List.of(
                new AssetReturnRow(AnalyticsInstrumentType.SPOT, "AAA", "A", pa),
                new AssetReturnRow(AnalyticsInstrumentType.SPOT, "BBB", "B", pb)));
        when(assetReturnsService.peekReturns()).thenReturn(dataset);

        // Act
        AssetReturnsData data = provider.fetch("sub", sectionFor("{\"currency\":\"USD\"}"));

        // Assert
        assertThat(data.currency()).isEqualTo("USD");
        assertThat(data.entries()).extracting(AssetReturnsData.ReturnRow::code).containsExactly("BBB", "AAA");
        assertThat(data.entries().get(0).returnPct()).isEqualByComparingTo("40");
    }

    private PeriodReturn ret(double pct, Double vol, RiskLevel risk) {
        return new PeriodReturn(
                BigDecimal.valueOf(pct), BigDecimal.valueOf(pct),
                BigDecimal.ONE, BigDecimal.valueOf(2),
                vol == null ? null : BigDecimal.valueOf(vol), risk,
                null, null);
    }

    private AssetReturnRow row(AnalyticsInstrumentType type, String code, String period,
                               double pct, Double vol, RiskLevel risk) {
        Map<String, PeriodReturn> periods = new LinkedHashMap<>();
        periods.put(period, ret(pct, vol, risk));
        return new AssetReturnRow(type, code, code + " Name", periods);
    }

    private AssetReturnsResponse dataset(List<AssetReturnRow> rows) {
        return new AssetReturnsResponse(LocalDate.now(), rows);
    }

    private AssetReturnsResponse seriesOf(int count, String period) {
        List<AssetReturnRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(row(AnalyticsInstrumentType.SPOT, "C" + i, period, 100 - i, 20.0, RiskLevel.LOW));
        }
        return dataset(rows);
    }

    @Test
    void shouldReturnAssetReturnsKind_whenKindQueried() {
        WidgetKind kind = provider.kind();

        assertThat(kind).isEqualTo(WidgetKind.ASSET_RETURNS);
    }

    @Test
    void shouldReturnEmptyAndTriggerWarm_whenCacheCold() {
        when(assetReturnsService.peekReturns()).thenReturn(null);

        AssetReturnsData data = provider.fetch("u", section);

        assertThat(data.period()).isEqualTo("1Y");
        assertThat(data.entries()).isEmpty();
        verify(assetReturnsService).warmAsync();
    }

    @Test
    void shouldReturnEmptyWithoutWarm_whenPeekThrows() {
        when(assetReturnsService.peekReturns()).thenThrow(new RuntimeException("boom"));

        AssetReturnsData data = provider.fetch("u", section);

        assertThat(data.entries()).isEmpty();
        verify(assetReturnsService, never()).warmAsync();
    }

    @Test
    void shouldUseDefaultPeriodAndSortReturnDescending_whenConfigEmpty() {
        when(assetReturnsService.peekReturns()).thenReturn(dataset(List.of(
                row(AnalyticsInstrumentType.SPOT, "LOW", "1Y", 10, 20.0, RiskLevel.LOW),
                row(AnalyticsInstrumentType.SPOT, "HIGH", "1Y", 90, 20.0, RiskLevel.LOW),
                row(AnalyticsInstrumentType.SPOT, "MID", "1Y", 50, 20.0, RiskLevel.LOW))));

        AssetReturnsData data = provider.fetch("u", section);

        assertThat(data.period()).isEqualTo("1Y");
        assertThat(data.sortBy()).isEqualTo("RETURN");
        assertThat(data.entries()).extracting(AssetReturnsData.ReturnRow::code)
                .containsExactly("HIGH", "MID", "LOW");
    }

    @Test
    void shouldSelectConfiguredPeriod_andSkipAssetsMissingIt() {
        AssetReturnRow has3m = new AssetReturnRow(AnalyticsInstrumentType.FUND, "F1", "F1 Name",
                Map.of("3M", ret(12, 10.0, RiskLevel.LOW)));
        AssetReturnRow only1y = new AssetReturnRow(AnalyticsInstrumentType.FUND, "F2", "F2 Name",
                Map.of("1Y", ret(40, 10.0, RiskLevel.LOW)));
        when(assetReturnsService.peekReturns()).thenReturn(dataset(List.of(has3m, only1y)));

        AssetReturnsData data = provider.fetch("u", sectionFor("{\"period\":\"3M\"}"));

        assertThat(data.period()).isEqualTo("3M");
        assertThat(data.entries()).extracting(AssetReturnsData.ReturnRow::code).containsExactly("F1");
    }

    @Test
    void shouldFilterByAssetType_whenAssetTypeProvided() {
        when(assetReturnsService.peekReturns()).thenReturn(dataset(List.of(
                row(AnalyticsInstrumentType.SPOT, "AKBNK", "1Y", 60, 20.0, RiskLevel.LOW),
                row(AnalyticsInstrumentType.CRYPTO, "BTC", "1Y", 90, 20.0, RiskLevel.HIGH),
                row(AnalyticsInstrumentType.FOREX, "USD", "1Y", 30, 20.0, RiskLevel.LOW))));

        AssetReturnsData data = provider.fetch("u", sectionFor("{\"assetType\":\"crypto\"}"));

        assertThat(data.entries()).extracting(AssetReturnsData.ReturnRow::code).containsExactly("BTC");
    }

    @Test
    void shouldIgnoreAssetTypeFilter_whenValueIsAll() {
        when(assetReturnsService.peekReturns()).thenReturn(dataset(List.of(
                row(AnalyticsInstrumentType.SPOT, "A", "1Y", 60, 20.0, RiskLevel.LOW),
                row(AnalyticsInstrumentType.CRYPTO, "B", "1Y", 30, 20.0, RiskLevel.LOW))));

        AssetReturnsData data = provider.fetch("u", sectionFor("{\"assetType\":\"ALL\"}"));

        assertThat(data.entries()).hasSize(2);
    }

    @Test
    void shouldFilterByRisk_whenRiskProvided() {
        when(assetReturnsService.peekReturns()).thenReturn(dataset(List.of(
                row(AnalyticsInstrumentType.SPOT, "SAFE", "1Y", 20, 5.0, RiskLevel.LOW),
                row(AnalyticsInstrumentType.CRYPTO, "WILD", "1Y", 200, 90.0, RiskLevel.HIGH))));

        AssetReturnsData data = provider.fetch("u", sectionFor("{\"risk\":\"HIGH\"}"));

        assertThat(data.entries()).extracting(AssetReturnsData.ReturnRow::code).containsExactly("WILD");
    }

    @Test
    void shouldSortByRiskAdjusted_whenSortByRiskAdj() {
        // B: 60/10 = 6.0 risk-adjusted beats A: 100/50 = 2.0, even though A has the higher raw return.
        when(assetReturnsService.peekReturns()).thenReturn(dataset(List.of(
                row(AnalyticsInstrumentType.SPOT, "A", "1Y", 100, 50.0, RiskLevel.HIGH),
                row(AnalyticsInstrumentType.SPOT, "B", "1Y", 60, 10.0, RiskLevel.LOW))));

        AssetReturnsData data = provider.fetch("u", sectionFor("{\"sortBy\":\"RISK_ADJ\"}"));

        assertThat(data.entries()).extracting(AssetReturnsData.ReturnRow::code).containsExactly("B", "A");
    }

    @Test
    void shouldSinkUnknownVolatilityToBottom_whenSortByVolatilityDescending() {
        when(assetReturnsService.peekReturns()).thenReturn(dataset(List.of(
                row(AnalyticsInstrumentType.SPOT, "NOVOL", "1Y", 40, null, null),
                row(AnalyticsInstrumentType.SPOT, "HIGHVOL", "1Y", 40, 80.0, RiskLevel.HIGH),
                row(AnalyticsInstrumentType.SPOT, "LOWVOL", "1Y", 40, 10.0, RiskLevel.LOW))));

        AssetReturnsData data = provider.fetch("u", sectionFor("{\"sortBy\":\"VOLATILITY\"}"));

        assertThat(data.entries()).extracting(AssetReturnsData.ReturnRow::code)
                .containsExactly("HIGHVOL", "LOWVOL", "NOVOL");
    }

    @Test
    void shouldReverseOrder_whenSortDirAscending() {
        when(assetReturnsService.peekReturns()).thenReturn(dataset(List.of(
                row(AnalyticsInstrumentType.SPOT, "HIGH", "1Y", 90, 20.0, RiskLevel.LOW),
                row(AnalyticsInstrumentType.SPOT, "LOW", "1Y", 10, 20.0, RiskLevel.LOW))));

        AssetReturnsData data = provider.fetch("u", sectionFor("{\"sortDir\":\"ASC\"}"));

        assertThat(data.entries()).extracting(AssetReturnsData.ReturnRow::code).containsExactly("LOW", "HIGH");
    }

    @ParameterizedTest
    @CsvSource({"1, 5", "100, 20", "8, 8"})
    void shouldClampLimit_whenRequested(int requested, int expectedSize) {
        when(assetReturnsService.peekReturns()).thenReturn(seriesOf(25, "1Y"));

        AssetReturnsData data = provider.fetch("u", sectionFor("{\"limit\":" + requested + "}"));

        assertThat(data.entries()).hasSize(expectedSize);
    }

    @Test
    void shouldMapRowFields_whenEntriesPresent() {
        when(assetReturnsService.peekReturns()).thenReturn(dataset(List.of(
                row(AnalyticsInstrumentType.CRYPTO, "BTC", "1Y", 90, 65.0, RiskLevel.HIGH))));

        AssetReturnsData data = provider.fetch("u", section);

        AssetReturnsData.ReturnRow r = data.entries().get(0);
        assertThat(r.type()).isEqualTo("CRYPTO");
        assertThat(r.code()).isEqualTo("BTC");
        assertThat(r.name()).isEqualTo("BTC Name");
        assertThat(r.returnPct()).isEqualByComparingTo("90");
        assertThat(r.volatility()).isEqualByComparingTo("65.0");
        assertThat(r.riskLevel()).isEqualTo("HIGH");
    }
}
