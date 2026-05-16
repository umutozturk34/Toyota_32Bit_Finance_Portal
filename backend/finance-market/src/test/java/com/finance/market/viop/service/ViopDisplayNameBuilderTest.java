package com.finance.market.viop.service;

import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.model.ViopOptionSide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ViopDisplayNameBuilderTest {

    @ParameterizedTest
    @CsvSource({
            "OPTION, AKBNK, CALL, 45.50, 2026-05-31, AKBNK Call 45.5 · 31 May 26",
            "OPTION, TOASO, PUT, 290.00, 2026-06-30, TOASO Put 290 · 30 Haz 26",
            "OPTION, EREGL, PUT, 41.00, 2026-05-31, EREGL Put 41 · 31 May 26"
    })
    void should_buildOptionName_when_allFieldsProvided(String kind, String underlying, String side,
                                                       String strike, String expiry, String expected) {
        ViopContractKind k = ViopContractKind.valueOf(kind);
        ViopOptionSide s = ViopOptionSide.valueOf(side);
        BigDecimal strikePrice = new BigDecimal(strike);
        LocalDate expiryDate = LocalDate.parse(expiry);

        String result = ViopDisplayNameBuilder.build(k, underlying, s, strikePrice, expiryDate);

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "USDTRY, 2026-06-30, USDTRY Vadeli · 30 Haz 26",
            "XU030, 2026-12-31, XU030 Vadeli · 31 Ara 26"
    })
    void should_buildFutureName_when_kindIsFuture(String underlying, String expiry, String expected) {
        LocalDate expiryDate = LocalDate.parse(expiry);

        String result = ViopDisplayNameBuilder.build(ViopContractKind.FUTURE, underlying, null, null, expiryDate);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void should_returnNull_when_kindIsNull() {
        String result = ViopDisplayNameBuilder.build(null, "AKBNK", ViopOptionSide.CALL,
                new BigDecimal("45.50"), LocalDate.of(2026, 5, 31));

        assertThat(result).isNull();
    }

    @Test
    void should_omitOptionSideAndStrike_when_optionMissingDetails() {
        String result = ViopDisplayNameBuilder.build(ViopContractKind.OPTION, "AKBNK",
                null, null, LocalDate.of(2026, 5, 31));

        assertThat(result).isEqualTo("AKBNK · 31 May 26");
    }

    @Test
    void should_includeOnlyExpiry_when_underlyingMissing() {
        String result = ViopDisplayNameBuilder.build(ViopContractKind.FUTURE, null, null, null,
                LocalDate.of(2026, 6, 30));

        assertThat(result).isEqualTo("Vadeli · 30 Haz 26");
    }

    @Test
    void should_stripTrailingZeros_when_strikeIsRoundNumber() {
        String result = ViopDisplayNameBuilder.build(ViopContractKind.OPTION, "AKBNK",
                ViopOptionSide.CALL, new BigDecimal("45.000"), LocalDate.of(2026, 5, 31));

        assertThat(result).contains("Call 45 ");
    }

    @Test
    void should_handleNullExpiry_when_otherFieldsPresent() {
        String result = ViopDisplayNameBuilder.build(ViopContractKind.OPTION, "AKBNK",
                ViopOptionSide.CALL, new BigDecimal("45.50"), null);

        assertThat(result).isEqualTo("AKBNK Call 45.5");
    }

    @Test
    void should_returnNull_when_allOptionalsMissing() {
        String result = ViopDisplayNameBuilder.build(ViopContractKind.OPTION, null, null, null, null);

        assertThat(result).isNull();
    }
}
