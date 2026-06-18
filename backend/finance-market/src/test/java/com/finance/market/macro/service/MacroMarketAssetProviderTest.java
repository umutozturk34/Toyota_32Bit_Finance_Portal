package com.finance.market.macro.service;

import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.MarketAssetProvider.MarketAssetFilters;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroFrequency;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MacroMarketAssetProviderTest {

    @Mock private MacroIndicatorQueryService queryService;
    @Mock private MessageSource messageSource;

    private MacroMarketAssetProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MacroMarketAssetProvider(queryService, MacroCategory.RATES, messageSource) {};
        when(messageSource.getMessage(any(String.class), any(), any(String.class), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(2));
    }

    private MacroIndicator indicator(String code, String label, MacroCategory category,
                                     BigDecimal lastValue) {
        return indicator(code, label, category, lastValue, false);
    }

    private MacroIndicator indicator(String code, String label, MacroCategory category,
                                     BigDecimal lastValue, boolean prominent) {
        Instrument instrument = Instrument.create(category.instrumentType(), code);
        MacroIndicator i = MacroIndicator.builder()
                .instrument(instrument)
                .code(code)
                .label(label)
                .category(category)
                .unit(MacroUnit.PERCENT)
                .frequency(MacroFrequency.DAILY)
                .prominent(prominent)
                .build();
        if (lastValue != null) {
            i.recordObservation(LocalDate.of(2026, 5, 1), lastValue);
        }
        return i;
    }

    @Test
    void should_returnCategoryInstrumentType_when_getTypeCalled() {
        // Arrange
        // provider built with RATES category in setUp

        // Act
        MarketType type = provider.getType();

        // Assert
        assertThat(type).isEqualTo(MarketType.MACRO_RATE);
    }

    @Test
    void should_returnResponse_when_codeMatchesCategory() {
        // Arrange
        MacroIndicator ind = indicator("TP.RATE", "policyRate", MacroCategory.RATES, new BigDecimal("42.5"));
        when(queryService.findByPublicId("TP.RATE")).thenReturn(ind);

        // Act
        MarketAssetResponse response = provider.getByCode("TP.RATE");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("policyrate");
        assertThat(response.name()).isEqualTo("policyRate");
        assertThat(response.price()).isEqualByComparingTo("42.5");
        assertThat(response.type()).isEqualTo(MarketType.MACRO_RATE);
    }

    @Test
    void should_returnNull_when_codeBelongsToDifferentCategory() {
        // Arrange
        MacroIndicator ind = indicator("TUFE", "cpiYoY", MacroCategory.INFLATION, new BigDecimal("60"));
        when(queryService.findByPublicId("TUFE")).thenReturn(ind);

        // Act
        MarketAssetResponse response = provider.getByCode("TUFE");

        // Assert
        assertThat(response).isNull();
    }

    @Test
    void should_returnNull_when_queryServiceThrows() {
        // Arrange
        when(queryService.findByPublicId("ZZZ")).thenThrow(new RuntimeException("not found"));

        // Act
        MarketAssetResponse response = provider.getByCode("ZZZ");

        // Assert
        assertThat(response).isNull();
    }

    @Test
    void should_returnAll_when_searchTermIsNull() {
        // Arrange
        List<MacroIndicator> all = List.of(
                indicator("TP.RATE", "policyRate", MacroCategory.RATES, new BigDecimal("42")),
                indicator("FAIZ", "rate", MacroCategory.RATES, new BigDecimal("30"))
        );
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(all);

        // Act
        List<MarketAssetResponse> result = provider.search(null, MarketAssetFilters.none(),
                "default", "asc", 0, 10);

        // Assert
        assertThat(result).hasSize(2);
    }

    @Test
    void should_filterByAlias_when_searchTermIsKnownAlias() {
        // Arrange
        List<MacroIndicator> all = List.of(
                indicator("FAIZ", "interestRate", MacroCategory.RATES, new BigDecimal("30")),
                indicator("OTHER", "other", MacroCategory.RATES, new BigDecimal("5"))
        );
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(all);

        // Act
        List<MarketAssetResponse> result = provider.search("interest", MarketAssetFilters.none(),
                "default", "asc", 0, 10);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("interestrate");
    }

    @Test
    void should_filterByCodeContains_when_searchTermMatchesCode() {
        // Arrange
        List<MacroIndicator> all = List.of(
                indicator("TP.RATE", "policyRate", MacroCategory.RATES, new BigDecimal("42")),
                indicator("FAIZ", "interestRate", MacroCategory.RATES, new BigDecimal("30"))
        );
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(all);

        // Act
        List<MarketAssetResponse> result = provider.search("tp", MarketAssetFilters.none(),
                "name", "asc", 0, 10);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("policyrate");
    }

    @Test
    void should_returnEmpty_when_searchTermMatchesNothing() {
        // Arrange
        List<MacroIndicator> all = List.of(
                indicator("TP.RATE", "policyRate", MacroCategory.RATES, new BigDecimal("42"))
        );
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(all);

        // Act
        List<MarketAssetResponse> result = provider.search("nomatch", MarketAssetFilters.none(),
                "default", "desc", 0, 10);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void should_paginate_when_pageBeyondAvailableData() {
        // Arrange
        List<MacroIndicator> all = List.of(
                indicator("A", "a", MacroCategory.RATES, new BigDecimal("1")),
                indicator("B", "b", MacroCategory.RATES, new BigDecimal("2"))
        );
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(all);

        // Act
        List<MarketAssetResponse> result = provider.search(null, MarketAssetFilters.none(),
                "default", "asc", 5, 10);

        // Assert
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "price,asc",
            "price,desc",
            "name,asc",
            "name,desc",
            "default,asc",
            "default,desc",
            "unknown,asc"
    })
    void should_sortWithoutError_when_givenSortByAndDirection(String sortBy, String direction) {
        // Arrange
        List<MacroIndicator> all = List.of(
                indicator("A", "alpha", MacroCategory.RATES, new BigDecimal("1")),
                indicator("B", "beta", MacroCategory.RATES, new BigDecimal("2")),
                indicator("C", "gamma", MacroCategory.RATES, null)
        );
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(all);

        // Act
        List<MarketAssetResponse> result = provider.search(null, MarketAssetFilters.none(),
                sortBy, direction, 0, 10);

        // Assert
        assertThat(result).hasSize(3);
    }

    @Test
    void should_filterNullLastValueAndOrderDescending_when_topGainers() {
        // Arrange
        List<MacroIndicator> all = List.of(
                indicator("A", "a", MacroCategory.RATES, new BigDecimal("1")),
                indicator("B", "b", MacroCategory.RATES, new BigDecimal("10")),
                indicator("C", "c", MacroCategory.RATES, null),
                indicator("D", "d", MacroCategory.RATES, new BigDecimal("5"))
        );
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(all);

        // Act
        List<MarketAssetResponse> result = provider.getTopMovers(2, true);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("b");
        assertThat(result.get(1).code()).isEqualTo("d");
    }

    @Test
    void should_orderAscending_when_topLosers() {
        // Arrange
        List<MacroIndicator> all = List.of(
                indicator("A", "a", MacroCategory.RATES, new BigDecimal("1")),
                indicator("B", "b", MacroCategory.RATES, new BigDecimal("10")),
                indicator("C", "c", MacroCategory.RATES, new BigDecimal("5"))
        );
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(all);

        // Act
        List<MarketAssetResponse> result = provider.getTopMovers(2, false);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("a");
        assertThat(result.get(1).code()).isEqualTo("c");
    }

    @Test
    void should_returnCategorySize_when_count() {
        // Arrange
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(List.of(
                indicator("A", "a", MacroCategory.RATES, BigDecimal.ONE),
                indicator("B", "b", MacroCategory.RATES, BigDecimal.TEN)
        ));

        // Act
        long count = provider.count(MarketAssetFilters.none());

        // Assert
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void should_returnTotalCount_when_countBySearchHasEmptyQuery() {
        // Arrange
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(List.of(
                indicator("A", "a", MacroCategory.RATES, BigDecimal.ONE),
                indicator("B", "b", MacroCategory.RATES, BigDecimal.TEN)
        ));

        // Act
        long count = provider.countBySearch("   ", MarketAssetFilters.none());

        // Assert
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void should_returnFilteredCount_when_countBySearchHasQuery() {
        // Arrange
        when(queryService.listByCategory(MacroCategory.RATES)).thenReturn(List.of(
                indicator("FAIZ", "rate", MacroCategory.RATES, BigDecimal.ONE),
                indicator("OTHER", "other", MacroCategory.RATES, BigDecimal.TEN)
        ));

        // Act
        long count = provider.countBySearch("faiz", MarketAssetFilters.none());

        // Assert
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void should_returnCpiNotPpi_when_searchingTufe() {
        // Arrange — EVDS code naming is misleading: TP.GENENDEKS.T1 is CPI (TÜFE), TP.TUFE1YI.T1 is PPI (Yİ-ÜFE).
        MacroMarketAssetProvider inflation =
                new MacroMarketAssetProvider(queryService, MacroCategory.INFLATION, messageSource) {};
        when(queryService.listByCategory(MacroCategory.INFLATION)).thenReturn(List.of(
                indicator("TP.GENENDEKS.T1", "cpiIndex", MacroCategory.INFLATION, new BigDecimal("60")),
                indicator("TP.TUFE1YI.T1", "ppiIndex", MacroCategory.INFLATION, new BigDecimal("50"))
        ));

        // Act
        List<MarketAssetResponse> result = inflation.search("tüfe", MarketAssetFilters.none(),
                "default", "asc", 0, 10);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("cpiindex");
    }

    @Test
    void should_returnPpiNotCpi_when_searchingUfe() {
        // Arrange
        MacroMarketAssetProvider inflation =
                new MacroMarketAssetProvider(queryService, MacroCategory.INFLATION, messageSource) {};
        when(queryService.listByCategory(MacroCategory.INFLATION)).thenReturn(List.of(
                indicator("TP.GENENDEKS.T1", "cpiIndex", MacroCategory.INFLATION, new BigDecimal("60")),
                indicator("TP.TUFE1YI.T1", "ppiIndex", MacroCategory.INFLATION, new BigDecimal("50"))
        ));

        // Act
        List<MarketAssetResponse> result = inflation.search("üfe", MarketAssetFilters.none(),
                "default", "asc", 0, 10);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("ppiindex");
    }

    @Test
    void should_surfaceProminentFirst_when_defaultSort() {
        // Arrange — the prominent EUR Total sorts LAST by code (E<T<U) and was buried past the suggestion cap by
        // the old code-DESC default; prominent-first must surface it ahead of non-prominent USD tenors.
        MacroMarketAssetProvider deposits =
                new MacroMarketAssetProvider(queryService, MacroCategory.DEPOSIT, messageSource) {};
        when(queryService.listByCategory(MacroCategory.DEPOSIT)).thenReturn(List.of(
                indicator("TP.USDTAS.MT01", "depositUsd1m", MacroCategory.DEPOSIT, new BigDecimal("3")),
                indicator("TP.USDTAS.MT02", "depositUsd3m", MacroCategory.DEPOSIT, new BigDecimal("3")),
                indicator("TP.EURTAS.MT06", "depositEurTotal", MacroCategory.DEPOSIT, new BigDecimal("2"), true)
        ));

        // Act — single-slot page: only the top-ranked result survives
        List<MarketAssetResponse> result = deposits.search(null, MarketAssetFilters.none(),
                "default", "desc", 0, 1);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("depositeurtotal");
    }

    @Test
    void should_returnNullName_when_indicatorLabelIsNull() {
        // Arrange
        MacroIndicator ind = indicator("CODE1", null, MacroCategory.RATES, new BigDecimal("3"));
        when(queryService.findByPublicId("CODE1")).thenReturn(ind);

        // Act
        MarketAssetResponse response = provider.getByCode("CODE1");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.name()).isNull();
    }
}
