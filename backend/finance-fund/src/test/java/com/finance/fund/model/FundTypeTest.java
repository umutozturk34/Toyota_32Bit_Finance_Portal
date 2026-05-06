package com.finance.fund.model;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

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
