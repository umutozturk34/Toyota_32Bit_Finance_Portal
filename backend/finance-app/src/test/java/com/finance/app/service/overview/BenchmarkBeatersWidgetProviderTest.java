package com.finance.app.service.overview;

import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.response.InflationBeaterEntry;
import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.app.analytics.service.InflationBeaterService;
import com.finance.app.dto.response.overview.BenchmarkBeatersData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.common.model.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkBeatersWidgetProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private InflationBeaterService inflationBeaterService;

    @InjectMocks
    private BenchmarkBeatersWidgetProvider provider;

    private WidgetSection section;

    @BeforeEach
    void setUp() {
        section = sectionFor("{}");
    }

    private WidgetSection sectionFor(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new WidgetSection("bb-1", WidgetKind.BENCHMARK_BEATERS, 0, node);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private InflationBeaterEntry entry(String code, BigDecimal nominal, BigDecimal excess, boolean beats) {
        return new InflationBeaterEntry(AnalyticsInstrumentType.SPOT, code, code + " Name", nominal, excess, beats);
    }

    private InflationBeaterEntry entryWithType(AnalyticsInstrumentType type, String code,
                                               BigDecimal nominal, BigDecimal excess, boolean beats) {
        return new InflationBeaterEntry(type, code, code + " Name", nominal, excess, beats);
    }

    private InflationBeaterResponse response(List<InflationBeaterEntry> entries, BigDecimal benchmarkPct) {
        return new InflationBeaterResponse(
                LocalDate.now().minusYears(1), LocalDate.now(),
                "TP.TUFE1YI.T1", "TÜFE", benchmarkPct,
                (int) entries.stream().filter(InflationBeaterEntry::beatsBenchmark).count(),
                entries.size(), Currency.TRY, entries);
    }

    @Test
    void shouldReturnBenchmarkBeatersKind_whenKindQueried() {
        WidgetKind kind = provider.kind();

        assertThat(kind).isEqualTo(WidgetKind.BENCHMARK_BEATERS);
    }

    @Test
    void shouldReturnEmptyData_whenServiceThrowsRuntimeException() {
        when(inflationBeaterService.peekCache(anyString(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        BenchmarkBeatersData data = provider.fetch("user-1", section);

        assertThat(data.benchmarkCode()).isEqualTo("TP.TUFE1YI.T1");
        assertThat(data.benchmarkReturnPct()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.period()).isEqualTo("1Y");
        assertThat(data.entries()).isEmpty();
    }

    @Test
    void shouldUseDefaults_whenConfigEmpty() {
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(List.of(entry("A", new BigDecimal("50"), new BigDecimal("25"), true)),
                        new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("user-1", section);

        assertThat(data.period()).isEqualTo("1Y");
        assertThat(data.benchmarkCode()).isEqualTo("TP.TUFE1YI.T1");
        assertThat(data.benchmarkReturnPct()).isEqualByComparingTo("25");
        assertThat(data.entries()).hasSize(1);
    }

    @Test
    void shouldUseConfiguredBenchmarkAndPeriod_whenProvided() {
        InflationBeaterResponse stub = new InflationBeaterResponse(
                LocalDate.now().minusYears(1), LocalDate.now(),
                "CUSTOM.CODE", "Custom", BigDecimal.ZERO,
                0, 0, Currency.TRY, List.of());
        when(inflationBeaterService.peekCache("6M", "CUSTOM.CODE")).thenReturn(stub);

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"benchmarkCode\":\"CUSTOM.CODE\",\"period\":\"6M\"}"));

        assertThat(data.benchmarkCode()).isEqualTo("CUSTOM.CODE");
        assertThat(data.period()).isEqualTo("6M");
    }

    @Test
    void shouldFallbackToDefaults_whenConfigCarriesBlankStrings() {
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(List.of(), BigDecimal.ZERO));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"benchmarkCode\":\"   \",\"period\":\"  \"}"));

        assertThat(data.benchmarkCode()).isEqualTo("TP.TUFE1YI.T1");
        assertThat(data.period()).isEqualTo("1Y");
    }

    @Test
    void shouldFallbackToDefaults_whenConfigCarriesNullNodes() {
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(List.of(), BigDecimal.ZERO));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"benchmarkCode\":null,\"period\":null,\"limit\":null,\"verdict\":null,\"sortDir\":null,\"assetType\":null}"));

        assertThat(data.benchmarkCode()).isEqualTo("TP.TUFE1YI.T1");
        assertThat(data.period()).isEqualTo("1Y");
    }

    @Test
    void shouldDefaultBenchmarkReturnToZero_whenServiceReturnsNull() {
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(List.of(), null));

        BenchmarkBeatersData data = provider.fetch("u", section);

        assertThat(data.benchmarkReturnPct()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldFilterByAssetType_whenAssetTypeMatches() {
        List<InflationBeaterEntry> all = List.of(
                entryWithType(AnalyticsInstrumentType.SPOT, "S1", new BigDecimal("60"), new BigDecimal("30"), true),
                entryWithType(AnalyticsInstrumentType.FOREX, "USD", new BigDecimal("40"), new BigDecimal("15"), true),
                entryWithType(AnalyticsInstrumentType.CRYPTO, "BTC", new BigDecimal("90"), new BigDecimal("65"), true));
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(all, new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"assetType\":\"forex\"}"));

        assertThat(data.entries()).hasSize(1);
        assertThat(data.entries().get(0).code()).isEqualTo("USD");
    }

    @Test
    void shouldIgnoreAssetTypeFilter_whenValueIsAllOrBlank() {
        List<InflationBeaterEntry> all = List.of(
                entry("A", new BigDecimal("50"), new BigDecimal("25"), true),
                entry("B", new BigDecimal("30"), new BigDecimal("5"), true));
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(all, new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"assetType\":\"ALL\"}"));

        assertThat(data.entries()).hasSize(2);
    }

    @Test
    void shouldKeepOnlyWinners_whenVerdictIsWINNERS() {
        List<InflationBeaterEntry> all = List.of(
                entry("WIN1", new BigDecimal("60"), new BigDecimal("35"), true),
                entry("LOSE1", new BigDecimal("5"), new BigDecimal("-20"), false),
                entry("WIN2", new BigDecimal("40"), new BigDecimal("15"), true));
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(all, new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"verdict\":\"winners\"}"));

        assertThat(data.entries()).extracting(BenchmarkBeatersData.BeaterRow::code)
                .containsExactly("WIN1", "WIN2");
    }

    @Test
    void shouldKeepOnlyLosers_whenVerdictIsLOSERS() {
        List<InflationBeaterEntry> all = List.of(
                entry("WIN", new BigDecimal("60"), new BigDecimal("35"), true),
                entry("LOSE", new BigDecimal("5"), new BigDecimal("-20"), false));
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(all, new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"verdict\":\"LOSERS\"}"));

        assertThat(data.entries()).extracting(BenchmarkBeatersData.BeaterRow::code)
                .containsExactly("LOSE");
    }

    @Test
    void shouldTreatUnknownVerdictAsAll_whenValueNotRecognized() {
        List<InflationBeaterEntry> all = List.of(
                entry("WIN", new BigDecimal("60"), new BigDecimal("35"), true),
                entry("LOSE", new BigDecimal("5"), new BigDecimal("-20"), false));
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(all, new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"verdict\":\"GARBAGE\"}"));

        assertThat(data.entries()).hasSize(2);
    }

    @Test
    void shouldReverseOrder_whenSortDirAscending() {
        List<InflationBeaterEntry> ordered = List.of(
                entry("FIRST", new BigDecimal("60"), new BigDecimal("35"), true),
                entry("SECOND", new BigDecimal("30"), new BigDecimal("5"), true),
                entry("THIRD", new BigDecimal("10"), new BigDecimal("-15"), false));
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(ordered, new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"sortDir\":\"ASC\"}"));

        assertThat(data.entries()).extracting(BenchmarkBeatersData.BeaterRow::code)
                .containsExactly("THIRD", "SECOND", "FIRST");
    }

    @Test
    void shouldClampLimitBelowMinimum_whenRequestedLimitTooLow() {
        List<InflationBeaterEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries.add(entry("C" + i, new BigDecimal(50 - i), new BigDecimal(25 - i), i < 5));
        }
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(entries, new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"limit\":1}"));

        assertThat(data.entries()).hasSize(5);
    }

    @Test
    void shouldClampLimitAboveMaximum_whenRequestedLimitTooHigh() {
        List<InflationBeaterEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            entries.add(entry("C" + i, new BigDecimal(50 - i), new BigDecimal(25 - i), i < 5));
        }
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(entries, new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"limit\":100}"));

        assertThat(data.entries()).hasSize(20);
    }

    @Test
    void shouldUseDefaultLimit_whenLimitIsNotIntegerNode() {
        List<InflationBeaterEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            entries.add(entry("C" + i, new BigDecimal(50 - i), new BigDecimal(25 - i), true));
        }
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(entries, new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("u",
                sectionFor("{\"limit\":\"junk\"}"));

        assertThat(data.entries()).hasSize(10);
    }

    @Test
    void shouldMapRowFields_whenEntriesPresent() {
        when(inflationBeaterService.peekCache("1Y", "TP.TUFE1YI.T1"))
                .thenReturn(response(List.of(entryWithType(AnalyticsInstrumentType.CRYPTO, "BTC",
                                new BigDecimal("90"), new BigDecimal("65"), true)),
                        new BigDecimal("25")));

        BenchmarkBeatersData data = provider.fetch("u", section);

        BenchmarkBeatersData.BeaterRow row = data.entries().get(0);
        assertThat(row.code()).isEqualTo("BTC");
        assertThat(row.name()).isEqualTo("BTC Name");
        assertThat(row.type()).isEqualTo("CRYPTO");
        assertThat(row.nominalReturnPct()).isEqualByComparingTo("90");
        assertThat(row.excessReturnPct()).isEqualByComparingTo("65");
        assertThat(row.beatsBenchmark()).isTrue();
    }
}
