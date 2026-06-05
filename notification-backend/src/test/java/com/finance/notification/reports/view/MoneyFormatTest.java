package com.finance.notification.reports.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyFormatTest {

    @Test
    void should_returnDash_when_valueIsNull() {
        MoneyFormat format = new MoneyFormat("₺", Locale.forLanguageTag("tr"));

        String result = format.of(null);

        assertThat(result).isEqualTo("—");
    }

    @Test
    void should_returnDashForSigned_when_valueIsNull() {
        MoneyFormat format = new MoneyFormat("₺", Locale.forLanguageTag("tr"));

        String result = format.signed(null);

        assertThat(result).isEqualTo("—");
    }

    @Test
    void should_useTurkishSeparators_when_localeIsTr() {
        MoneyFormat format = new MoneyFormat("₺", Locale.forLanguageTag("tr"));

        String result = format.of(new BigDecimal("123.45"));

        assertThat(result).isEqualTo("₺ 123,45");
    }

    @Test
    void should_useEnglishSeparators_when_localeIsEn() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.of(new BigDecimal("123.45"));

        assertThat(result).isEqualTo("$ 123.45");
    }

    @Test
    void should_defaultToEnglishSeparators_when_localeIsNull() {
        MoneyFormat format = new MoneyFormat("$", null);

        String result = format.of(new BigDecimal("1234.5"));

        assertThat(result).isEqualTo("$ 1.23K");
    }

    @Test
    void should_formatPlainTwoDecimals_when_valueBelowThousand() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.of(new BigDecimal("0"));

        assertThat(result).isEqualTo("$ 0.00");
    }

    @Test
    void should_formatCompactKilo_when_valueBetweenThousandAndMillion() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.of(new BigDecimal("1500"));

        assertThat(result).isEqualTo("$ 1.5K");
    }

    @Test
    void should_formatCompactMillion_when_valueBetweenMillionAndBillion() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.of(new BigDecimal("2500000"));

        assertThat(result).isEqualTo("$ 2.5M");
    }

    @Test
    void should_formatCompactBillion_when_valueAtLeastBillion() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.of(new BigDecimal("3500000000"));

        assertThat(result).isEqualTo("$ 3.5B");
    }

    @Test
    void should_stripTrailingZeros_when_compactBandRoundsClean() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.of(new BigDecimal("2000000"));

        assertThat(result).isEqualTo("$ 2M");
    }

    @Test
    void should_prependMinusSign_when_valueIsNegative() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.of(new BigDecimal("-1500"));

        assertThat(result).isEqualTo("−$ 1.5K");
    }

    @Test
    void should_prependMinusSignForPlainBand_when_valueIsSmallNegative() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.of(new BigDecimal("-12.5"));

        assertThat(result).isEqualTo("−$ 12.50");
    }

    @Test
    void should_groupIntegerPart_when_valueHasManyDigitsBelowThousandWithDecimals() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.of(new BigDecimal("999.99"));

        assertThat(result).isEqualTo("$ 999.99");
    }

    @Test
    void should_prefixPlusForPositive_when_usingSigned() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.signed(new BigDecimal("100"));

        assertThat(result).isEqualTo("+$ 100.00");
    }

    @Test
    void should_notPrefixPlusForNegative_when_usingSigned() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.signed(new BigDecimal("-100"));

        assertThat(result).isEqualTo("−$ 100.00");
    }

    @Test
    void should_treatZeroAsPositive_when_usingSigned() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.signed(BigDecimal.ZERO);

        assertThat(result).isEqualTo("+$ 0.00");
    }

    @ParameterizedTest
    @CsvSource({
            "1000,$ 1K",
            "1000000,$ 1M",
            "1234567,$ 1.23M",
            "1000000000,$ 1B",
            "1500000000,$ 1.5B"
    })
    void should_formatCompactBands_when_valuesCrossBoundaries(String input, String expected) {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.of(new BigDecimal(input));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void should_groupTurkishThousands_when_compactStripsToInteger() {
        MoneyFormat format = new MoneyFormat("₺", Locale.forLanguageTag("tr"));

        String result = format.of(new BigDecimal("1234567"));

        assertThat(result).isEqualTo("₺ 1,23M");
    }

    @Test
    void should_keepThreeCompactDecimals_when_usingOfPrecise() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.ofPrecise(new BigDecimal("117463"));

        assertThat(result).isEqualTo("$ 117.463K");
    }

    @Test
    void should_stripTrailingZeros_when_ofPreciseRoundsClean() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.ofPrecise(new BigDecimal("117400"));

        assertThat(result).isEqualTo("$ 117.4K");
    }

    @Test
    void should_keepTwoDecimals_when_ofPreciseValueBelowThousand() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.ofPrecise(new BigDecimal("123.45"));

        assertThat(result).isEqualTo("$ 123.45");
    }

    @Test
    void should_useTurkishSeparators_when_ofPreciseLocaleIsTr() {
        MoneyFormat format = new MoneyFormat("₺", Locale.forLanguageTag("tr"));

        String result = format.ofPrecise(new BigDecimal("117463"));

        assertThat(result).isEqualTo("₺ 117,463K");
    }

    @Test
    void should_returnDash_when_ofPreciseValueIsNull() {
        MoneyFormat format = new MoneyFormat("₺", Locale.forLanguageTag("tr"));

        String result = format.ofPrecise(null);

        assertThat(result).isEqualTo("—");
    }

    @Test
    void should_prefixPlusWithThreeDecimals_when_usingSignedPrecise() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.signedPrecise(new BigDecimal("117463"));

        assertThat(result).isEqualTo("+$ 117.463K");
    }

    @Test
    void should_returnDash_when_signedPreciseValueIsNull() {
        MoneyFormat format = new MoneyFormat("$", Locale.ENGLISH);

        String result = format.signedPrecise(null);

        assertThat(result).isEqualTo("—");
    }
}
