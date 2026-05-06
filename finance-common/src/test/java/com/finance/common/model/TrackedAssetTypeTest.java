package com.finance.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TrackedAssetTypeTest {

    @ParameterizedTest
    @CsvSource({
            "CRYPTO,    CRYPTO",
            "STOCK,     STOCK",
            "FUND,      FUND",
            "COMMODITY, COMMODITY"
    })
    void marketTypeMapsEachConstantToMatchingMarketType(TrackedAssetType tracked, MarketType expected) {
        MarketType actual = tracked.marketType();

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "CRYPTO,    bitcoin,          bitcoin",
            "CRYPTO,    BITCOIN,          bitcoin",
            "CRYPTO,    '  ETH  ',        eth",
            "STOCK,     thyao,            THYAO",
            "STOCK,     '  xu100 ',       XU100",
            "FUND,      afa,              AFA",
            "COMMODITY, gold,             GOLD",
            "COMMODITY, '  xautryg  ',    XAUTRYG"
    })
    void normalizeCodeRespectsPerTypeCasingRules(TrackedAssetType tracked, String raw, String expected) {
        String actual = tracked.normalizeCode(raw);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void normalizeCodeIsLocaleIndependentForTurkishDottedI() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));

            String crypto = TrackedAssetType.CRYPTO.normalizeCode("BITCOIN");
            String stock = TrackedAssetType.STOCK.normalizeCode("thyao");

            assertThat(crypto).isEqualTo("bitcoin");
            assertThat(stock).isEqualTo("THYAO");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
