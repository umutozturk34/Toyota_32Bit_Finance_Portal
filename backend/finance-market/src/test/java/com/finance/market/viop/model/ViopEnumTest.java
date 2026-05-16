package com.finance.market.viop.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViopEnumTest {

    @Test
    void should_returnDaily_when_resolutionMinutesEquals1440() {
        ViopHistoryResolution r = ViopHistoryResolution.fromPeriodMinutes(1440);

        assertThat(r).isEqualTo(ViopHistoryResolution.DAILY);
        assertThat(r.periodMinutes()).isEqualTo(1440);
    }

    @Test
    void should_returnH1_when_resolutionMinutesEquals60() {
        assertThat(ViopHistoryResolution.fromPeriodMinutes(60)).isEqualTo(ViopHistoryResolution.H1);
    }

    @Test
    void should_throwIllegalArgument_when_unsupportedResolutionMinutes() {
        assertThatThrownBy(() -> ViopHistoryResolution.fromPeriodMinutes(7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported VIOP history resolution");
    }

    @Test
    void should_returnEmpty_when_resolveFilterCalledWithNull() {
        List<ViopCategory> result = ViopCategory.resolveFilter(null);

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnEmpty_when_resolveFilterCalledWithBlank() {
        List<ViopCategory> result = ViopCategory.resolveFilter("   ");

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnExactCategory_when_resolveFilterMatchesName() {
        List<ViopCategory> result = ViopCategory.resolveFilter("CURRENCY_FUTURE_TRY");

        assertThat(result).containsExactly(ViopCategory.CURRENCY_FUTURE_TRY);
    }

    @Test
    void should_returnAllCategoriesInClass_when_resolveFilterMatchesUnderlyingClass() {
        List<ViopCategory> result = ViopCategory.resolveFilter("CURRENCY");

        assertThat(result).contains(ViopCategory.CURRENCY_FUTURE_TRY, ViopCategory.CURRENCY_FUTURE_USD, ViopCategory.CURRENCY_OPTION);
    }

    @Test
    void should_returnEmpty_when_resolveFilterMatchesNothing() {
        List<ViopCategory> result = ViopCategory.resolveFilter("UNKNOWN_GROUP");

        assertThat(result).isEmpty();
    }

    @Test
    void should_exposeUnderlyingClass_when_categoryConfigured() {
        assertThat(ViopCategory.CURRENCY_FUTURE_TRY.underlyingClass()).isEqualTo(ViopUnderlyingClass.CURRENCY);
        assertThat(ViopCategory.INDEX_OPTION.underlyingClass()).isEqualTo(ViopUnderlyingClass.INDEX);
    }
}
