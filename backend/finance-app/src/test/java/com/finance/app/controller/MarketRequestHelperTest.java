package com.finance.app.controller;

import com.finance.common.exception.BadRequestException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketRequestHelperTest {

    @Test
    void parseMarketTypes_returnsAllValues_whenInputBlank() {
        List<MarketType> result = MarketRequestHelper.parseMarketTypes("  ");

        assertThat(result).containsExactly(MarketType.values());
    }

    @Test
    void parseMarketTypes_returnsAllValues_whenInputNull() {
        List<MarketType> result = MarketRequestHelper.parseMarketTypes(null);

        assertThat(result).containsExactly(MarketType.values());
    }

    @Test
    void parseMarketTypes_parsesCommaSeparatedValues_trimsAndUppercases() {
        List<MarketType> result = MarketRequestHelper.parseMarketTypes("stock,  crypto ,Forex");

        assertThat(result).containsExactly(MarketType.STOCK, MarketType.CRYPTO, MarketType.FOREX);
    }

    @Test
    void parseMarketTypes_throwsBadRequest_whenInvalidValue() {
        assertThatThrownBy(() -> MarketRequestHelper.parseMarketTypes("BOGUS"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void parseTrackedTypes_returnsAllValues_whenInputBlank() {
        List<TrackedAssetType> result = MarketRequestHelper.parseTrackedTypes("");

        assertThat(result).containsExactly(TrackedAssetType.values());
    }

    @Test
    void parseTrackedTypes_parsesAndNormalizes() {
        List<TrackedAssetType> result = MarketRequestHelper.parseTrackedTypes("stock, crypto");

        assertThat(result).containsExactly(TrackedAssetType.STOCK, TrackedAssetType.CRYPTO);
    }

    @ParameterizedTest
    @CsvSource({
            "10, 5, 50, 10",
            ", 5, 50, 5",
            "100, 5, 50, 50",
            "0, 5, 50, 1",
            "-3, 5, 50, 1"
    })
    void clamp_appliesDefaultAndMinMaxBounds(Integer value, int defaultVal, int max, int expected) {
        int result = MarketRequestHelper.clamp(value, defaultVal, max);

        assertThat(result).isEqualTo(expected);
    }
}
