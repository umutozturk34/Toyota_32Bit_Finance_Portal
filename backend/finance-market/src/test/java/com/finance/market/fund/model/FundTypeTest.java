package com.finance.market.fund.model;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundTypeTest {

    @ParameterizedTest
    @CsvSource({
            "BYF, true,  false",
            "YAT, false, true"
    })
    void scalingPredicatesMarkBulletinPriceForByfAndInvestorCountForYat(
            FundType type,
            boolean expectsBulletinPrice,
            boolean expectsInvestorCount) {
        assertThat(type.scalesBulletinPrice()).isEqualTo(expectsBulletinPrice);
        assertThat(type.scalesInvestorCount()).isEqualTo(expectsInvestorCount);
    }

    @ParameterizedTest
    @EnumSource(FundType.class)
    void exactlyOneScalingPredicateIsTrueForEachType(FundType type) {
        boolean anyScaling = type.scalesBulletinPrice() || type.scalesInvestorCount();
        boolean bothScalings = type.scalesBulletinPrice() && type.scalesInvestorCount();

        assertThat(anyScaling).isTrue();
        assertThat(bothScalings).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "BYF, BYF",
            "YAT, YAT"
    })
    void valueOfRoundTripPreservesName(String name, FundType expected) {
        assertThat(FundType.valueOf(name)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"byf", "yat", "UNKNOWN"})
    void valueOfRejectsUnknownNamesToCatchBoundaryTypos(String badName) {
        assertThatThrownBy(() -> FundType.valueOf(badName))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
