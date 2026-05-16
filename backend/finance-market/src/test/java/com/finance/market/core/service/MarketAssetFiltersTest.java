package com.finance.market.core.service;

import com.finance.market.core.service.MarketAssetProvider.MarketAssetFilters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketAssetFiltersTest {

    @Test
    void should_storeAllFourFields_when_fullConstructorUsed() {
        MarketAssetFilters filters = new MarketAssetFilters(
                "segment", "BYF", List.of("Hisse"), List.of(1, 7));

        assertThat(filters.segment()).isEqualTo("segment");
        assertThat(filters.subType()).isEqualTo("BYF");
        assertThat(filters.subCategories()).containsExactly("Hisse");
        assertThat(filters.riskValues()).containsExactly(1, 7);
    }

    @Test
    void should_passNullForSubCategoriesAndRiskValues_when_twoArgConstructorUsed() {
        MarketAssetFilters filters = new MarketAssetFilters("segment", "BYF");

        assertThat(filters.segment()).isEqualTo("segment");
        assertThat(filters.subType()).isEqualTo("BYF");
        assertThat(filters.subCategories()).isNull();
        assertThat(filters.riskValues()).isNull();
    }

    @Test
    void should_produceAllNullFilters_when_noneFactoryCalled() {
        MarketAssetFilters filters = MarketAssetFilters.none();

        assertThat(filters.segment()).isNull();
        assertThat(filters.subType()).isNull();
        assertThat(filters.subCategories()).isNull();
        assertThat(filters.riskValues()).isNull();
        assertThat(filters.hasSegment()).isFalse();
        assertThat(filters.hasSubType()).isFalse();
        assertThat(filters.hasSubCategories()).isFalse();
        assertThat(filters.hasRiskValues()).isFalse();
    }

    @Test
    void should_setOnlySegment_when_ofSegmentFactoryCalled() {
        MarketAssetFilters filters = MarketAssetFilters.ofSegment("STOCK");

        assertThat(filters.segment()).isEqualTo("STOCK");
        assertThat(filters.subType()).isNull();
        assertThat(filters.subCategories()).isNull();
        assertThat(filters.riskValues()).isNull();
        assertThat(filters.hasSegment()).isTrue();
    }

    @Test
    void should_returnTrueForHasSubCategories_when_listIsNonEmpty() {
        MarketAssetFilters filters = new MarketAssetFilters(
                null, null, List.of("Hisse Senedi"), null);

        assertThat(filters.hasSubCategories()).isTrue();
    }

    @Test
    void should_returnFalseForHasSubCategories_when_listIsNull() {
        MarketAssetFilters filters = new MarketAssetFilters(null, null, null, null);

        assertThat(filters.hasSubCategories()).isFalse();
    }

    @Test
    void should_returnFalseForHasSubCategories_when_listIsEmpty() {
        MarketAssetFilters filters = new MarketAssetFilters(
                null, null, List.of(), null);

        assertThat(filters.hasSubCategories()).isFalse();
    }

    @Test
    void should_returnTrueForHasRiskValues_when_listIsNonEmpty() {
        MarketAssetFilters filters = new MarketAssetFilters(
                null, null, null, List.of(3, 5));

        assertThat(filters.hasRiskValues()).isTrue();
    }

    @Test
    void should_returnFalseForHasRiskValues_when_listIsNull() {
        MarketAssetFilters filters = new MarketAssetFilters(null, null, null, null);

        assertThat(filters.hasRiskValues()).isFalse();
    }

    @Test
    void should_returnFalseForHasRiskValues_when_listIsEmpty() {
        MarketAssetFilters filters = new MarketAssetFilters(
                null, null, null, List.of());

        assertThat(filters.hasRiskValues()).isFalse();
    }

    @Test
    void should_returnTrueForHasSegment_when_segmentIsNonBlank() {
        MarketAssetFilters filters = MarketAssetFilters.ofSegment("STOCK");

        assertThat(filters.hasSegment()).isTrue();
    }

    @Test
    void should_returnFalseForHasSegment_when_segmentIsBlank() {
        MarketAssetFilters filters = MarketAssetFilters.ofSegment("   ");

        assertThat(filters.hasSegment()).isFalse();
    }

    @Test
    void should_returnTrueForHasSubType_when_subTypeIsNonBlank() {
        MarketAssetFilters filters = new MarketAssetFilters(null, "BYF");

        assertThat(filters.hasSubType()).isTrue();
    }

    @Test
    void should_returnFalseForHasSubType_when_subTypeIsBlank() {
        MarketAssetFilters filters = new MarketAssetFilters(null, "");

        assertThat(filters.hasSubType()).isFalse();
    }
}
