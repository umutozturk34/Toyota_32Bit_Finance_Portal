package com.finance.notification.reports.view;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class PercentFormatTest {

    @Test
    void should_returnDash_when_shareIsNull() {
        PercentFormat format = new PercentFormat(Locale.forLanguageTag("tr"));

        String result = format.share(null, new BigDecimal("10"));

        assertThat(result).isEqualTo("—");
    }

    @Test
    void should_formatShareWithTurkishSeparator_when_normalSlice() {
        PercentFormat format = new PercentFormat(Locale.forLanguageTag("tr"));

        String result = format.share(new BigDecimal("12.34"), new BigDecimal("100"));

        assertThat(result).isEqualTo("12,3%");
    }

    @Test
    void should_useSentinel_when_realSliceRoundsToZero() {
        PercentFormat format = new PercentFormat(Locale.forLanguageTag("tr"));

        String result = format.share(new BigDecimal("0.03"), new BigDecimal("80"));

        assertThat(result).isEqualTo("<0,1%");
    }

    @Test
    void should_notUseSentinel_when_valueIsZero() {
        PercentFormat format = new PercentFormat(Locale.ENGLISH);

        String result = format.share(new BigDecimal("0.03"), BigDecimal.ZERO);

        assertThat(result).isEqualTo("0.0%");
    }

    @Test
    void should_useEnglishSeparator_when_localeEn() {
        PercentFormat format = new PercentFormat(Locale.ENGLISH);

        String result = format.share(new BigDecimal("46.9"), new BigDecimal("4690"));

        assertThat(result).isEqualTo("46.9%");
    }

    @Test
    void should_keepDominantShare_when_roundsToHundred() {
        PercentFormat format = new PercentFormat(Locale.forLanguageTag("tr"));

        String result = format.share(new BigDecimal("100.0"), new BigDecimal("261000"));

        assertThat(result).isEqualTo("100,0%");
    }
}
