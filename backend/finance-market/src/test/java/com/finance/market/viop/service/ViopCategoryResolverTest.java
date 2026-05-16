package com.finance.market.viop.service;

import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContractKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ViopCategoryResolverTest {

    private final ViopCategoryResolver resolver = new ViopCategoryResolver();

    @ParameterizedTest
    @CsvSource({
            "OPTION, AKBNK, TRY, PAY_OPTION",
            "OPTION, XU030, TRY, INDEX_OPTION",
            "OPTION, USDTRY, TRY, CURRENCY_OPTION",
            "FUTURE, AKBNK, TRY, PAY_FUTURE",
            "FUTURE, XU030, TRY, INDEX_FUTURE",
            "FUTURE, USDTRY, TRY, CURRENCY_FUTURE_TRY",
            "FUTURE, EURUSD, USD, CURRENCY_FUTURE_USD",
            "FUTURE, XAU, USD, METAL_FUTURE_USD",
            "FUTURE, XAU, TRY, METAL_FUTURE_TRY",
            "FUTURE, XAG, TRY, METAL_FUTURE_TRY"
    })
    void should_returnExpectedCategory_when_resolvingByKindAndUnderlying(String kind, String underlying,
                                                                          String currency, String expected) {
        ViopCategory result = resolver.resolve(ViopContractKind.valueOf(kind), underlying, currency);

        assertThat(result).isEqualTo(ViopCategory.valueOf(expected));
    }

    @Test
    void should_treatNullUnderlying_asPayCategoryFallback() {
        assertThat(resolver.resolve(ViopContractKind.OPTION, null, null)).isEqualTo(ViopCategory.PAY_OPTION);
        assertThat(resolver.resolve(ViopContractKind.FUTURE, null, null)).isEqualTo(ViopCategory.PAY_FUTURE);
    }

    @Test
    void should_treatBlankUnderlying_asPayFutureFallback() {
        assertThat(resolver.resolve(ViopContractKind.FUTURE, "   ", null)).isEqualTo(ViopCategory.PAY_FUTURE);
    }

    @Test
    void should_normaliseDPrefix_when_underlyingHasDUnderscoreSegment() {
        ViopCategory result = resolver.resolve(ViopContractKind.FUTURE, "D_XU030", null);

        assertThat(result).isEqualTo(ViopCategory.INDEX_FUTURE);
    }

    @Test
    void should_truncateAtDot_when_underlyingHasSuffix() {
        ViopCategory result = resolver.resolve(ViopContractKind.FUTURE, "XU030.K", null);

        assertThat(result).isEqualTo(ViopCategory.INDEX_FUTURE);
    }
}
