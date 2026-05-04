package com.finance.common.service;

import com.finance.common.service.MarketAssetProvider.MarketAssetFilters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MarketAssetFiltersTest {

    @Test
    void noneFactoryProducesFilterWithNullSegmentAndSubType() {
        MarketAssetFilters filters = MarketAssetFilters.none();

        assertThat(filters.segment()).isNull();
        assertThat(filters.subType()).isNull();
        assertThat(filters.hasSegment()).isFalse();
        assertThat(filters.hasSubType()).isFalse();
    }

    @Test
    void ofSegmentFactoryProducesFilterWithGivenSegmentAndNullSubType() {
        MarketAssetFilters filters = MarketAssetFilters.ofSegment("MAIN_INDEX");

        assertThat(filters.segment()).isEqualTo("MAIN_INDEX");
        assertThat(filters.subType()).isNull();
        assertThat(filters.hasSegment()).isTrue();
        assertThat(filters.hasSubType()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EQUITY", "MAIN_INDEX", "SECONDARY_INDEX"})
    void hasSegmentIsTrueForNonBlankValues(String segment) {
        assertThat(new MarketAssetFilters(segment, null).hasSegment()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"   ", "\t"})
    void hasSegmentIsFalseForNullOrBlank(String segment) {
        assertThat(new MarketAssetFilters(segment, null).hasSegment()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "BYF, true",
            "YAT, true",
            "'', false"
    })
    void hasSubTypeReflectsPresenceOfNonBlankValue(String subType, boolean expected) {
        assertThat(new MarketAssetFilters(null, subType).hasSubType()).isEqualTo(expected);
    }

    @Test
    void filterCarriesBothSegmentAndSubTypeIndependently() {
        MarketAssetFilters filters = new MarketAssetFilters("MAIN_INDEX", "BYF");

        assertThat(filters.segment()).isEqualTo("MAIN_INDEX");
        assertThat(filters.subType()).isEqualTo("BYF");
        assertThat(filters.hasSegment()).isTrue();
        assertThat(filters.hasSubType()).isTrue();
    }
}
