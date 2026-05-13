package com.finance.market.core.util;

import com.finance.market.core.dto.internal.YahooChartResponse.Quote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YahooMetaHelpersTest {

    @Test
    void resolvePreviousClose_returnsMetaValue_whenMetaPresent() {
        BigDecimal meta = new BigDecimal("100");

        BigDecimal result = YahooMetaHelpers.resolvePreviousClose(null, meta);

        assertThat(result).isEqualByComparingTo(meta);
    }

    @Test
    void resolvePreviousClose_returnsNull_whenMetaNullAndQuoteNull() {
        BigDecimal result = YahooMetaHelpers.resolvePreviousClose(null, null);

        assertThat(result).isNull();
    }

    @Test
    void resolvePreviousClose_returnsNull_whenQuoteCloseNull() {
        Quote quote = new Quote(null, null, null, null, null);

        BigDecimal result = YahooMetaHelpers.resolvePreviousClose(quote, null);

        assertThat(result).isNull();
    }

    @Test
    void resolvePreviousClose_returnsSecondLastNonNullClose() {
        Quote quote = new Quote(null, null, null,
                Arrays.asList(new BigDecimal("100"), new BigDecimal("105"), new BigDecimal("110")), null);

        BigDecimal result = YahooMetaHelpers.resolvePreviousClose(quote, null);

        assertThat(result).isEqualByComparingTo(new BigDecimal("105"));
    }

    @Test
    void resolvePreviousClose_skipsNullClose_andReturnsLastNonNull() {
        Quote quote = new Quote(null, null, null,
                Arrays.asList(new BigDecimal("100"), null, null), null);

        BigDecimal result = YahooMetaHelpers.resolvePreviousClose(quote, null);

        assertThat(result).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void latestNonNull_returnsLastValue_whenAllNonNull() {
        BigDecimal result = YahooMetaHelpers.latestNonNull(List.of(
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3")));

        assertThat(result).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void latestNonNull_returnsLastNonNullBeforeTrailingNulls() {
        BigDecimal result = YahooMetaHelpers.latestNonNull(Arrays.asList(
                new BigDecimal("1"), new BigDecimal("2"), null, null));

        assertThat(result).isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    void latestNonNull_returnsNull_whenInputNullOrEmpty() {
        assertThat(YahooMetaHelpers.latestNonNull(null)).isNull();
        assertThat(YahooMetaHelpers.latestNonNull(List.of())).isNull();
    }
}
