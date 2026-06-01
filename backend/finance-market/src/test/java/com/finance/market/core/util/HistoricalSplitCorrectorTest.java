package com.finance.market.core.util;

import com.finance.market.core.dto.external.YahooCandleDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalSplitCorrectorTest {

    @Test
    void correct_appliesRatio_whenLargePostOverPreJump() {
        // Arrange — THYAO-style discontinuity (~1000×)
        YahooCandleDto pre = candle("2004-12-31T00:00", "0.0010", "0.0010", "0.0010", "0.0010");
        YahooCandleDto post = candle("2005-01-03T00:00", "1.0018", "1.0100", "0.9900", "1.0050");

        // Act
        List<YahooCandleDto> result = HistoricalSplitCorrector.correct(List.of(pre, post));

        // Assert — pre rescaled by ratio (1.0018/0.0010 = 1001.8), post untouched
        assertThat(result.get(0).close()).isEqualByComparingTo("1.0018");
        assertThat(result.get(1)).isSameAs(post);
    }

    @Test
    void correct_appliesRatio_whenInversePreOverPostJump() {
        // Arrange — pre 1000× larger than post (legacy unredenominated case)
        YahooCandleDto pre = candle("2004-12-31T00:00", "1000.0000", "1010.0000", "990.0000", "1000.0000");
        YahooCandleDto post = candle("2005-01-03T00:00", "1.0000", "1.0100", "0.9900", "1.0050");

        // Act
        List<YahooCandleDto> result = HistoricalSplitCorrector.correct(List.of(pre, post));

        // Assert — pre divided by 1000 to align with post
        assertThat(result.get(0).close()).isEqualByComparingTo("1.0000");
        assertThat(result.get(1)).isSameAs(post);
    }

    @Test
    void correct_leavesUntouched_whenJumpIsWithinSaneRange() {
        // Arrange — normal end-of-year price action (GARAN-style smooth continuation)
        YahooCandleDto pre = candle("2004-12-31T00:00", "1.4326", "1.4400", "1.4200", "1.4326");
        YahooCandleDto post = candle("2005-01-03T00:00", "1.4662", "1.4700", "1.4500", "1.4662");

        // Act
        List<YahooCandleDto> result = HistoricalSplitCorrector.correct(List.of(pre, post));

        // Assert — both candles returned as-is
        assertThat(result).containsExactly(pre, post);
    }

    @Test
    void correct_returnsInput_whenBatchHasNoPre2005Candles() {
        // Arrange
        YahooCandleDto only = candle("2025-04-15T00:00", "100.00", "101.00", "99.00", "100.50");

        // Act
        List<YahooCandleDto> result = HistoricalSplitCorrector.correct(List.of(only));

        // Assert
        assertThat(result).containsExactly(only);
    }

    @Test
    void correct_returnsInput_whenBatchHasNoPost2005Candles() {
        // Arrange
        YahooCandleDto only = candle("2003-06-01T00:00", "5000.00", "5100.00", "4900.00", "5050.00");

        // Act
        List<YahooCandleDto> result = HistoricalSplitCorrector.correct(List.of(only));

        // Assert
        assertThat(result).containsExactly(only);
    }

    @Test
    void correct_returnsInput_whenReferenceCloseIsZero() {
        // Arrange — division by zero guard
        YahooCandleDto pre = candle("2004-12-31T00:00", "0", "0", "0", "0");
        YahooCandleDto post = candle("2005-01-03T00:00", "1.0", "1.0", "1.0", "1.0");

        // Act
        List<YahooCandleDto> result = HistoricalSplitCorrector.correct(List.of(pre, post));

        // Assert
        assertThat(result).containsExactly(pre, post);
    }

    @Test
    void correct_returnsInput_whenNull() {
        assertThat(HistoricalSplitCorrector.correct(null)).isNull();
        assertThat(HistoricalSplitCorrector.correct(List.of())).isEmpty();
    }

    @Test
    void correct_scalesAllPre2005Candles_notJustTheBoundaryOne() {
        // Arrange — three pre-2005 candles, all must be scaled
        YahooCandleDto pre1 = candle("2003-06-01T00:00", "0.0008", "0.0009", "0.0007", "0.0008");
        YahooCandleDto pre2 = candle("2004-07-15T00:00", "0.0012", "0.0013", "0.0011", "0.0012");
        YahooCandleDto pre3 = candle("2004-12-31T00:00", "0.0010", "0.0010", "0.0010", "0.0010");
        YahooCandleDto post = candle("2005-01-03T00:00", "1.0018", "1.0100", "0.9900", "1.0050");

        // Act
        List<YahooCandleDto> result = HistoricalSplitCorrector.correct(List.of(pre1, pre2, pre3, post));

        // Assert — every pre-2005 candle scaled by 1001.8
        BigDecimal ratio = new BigDecimal("1001.8");
        assertThat(result.get(0).close()).isEqualByComparingTo(new BigDecimal("0.0008").multiply(ratio));
        assertThat(result.get(1).close()).isEqualByComparingTo(new BigDecimal("0.0012").multiply(ratio));
        assertThat(result.get(2).close()).isEqualByComparingTo(new BigDecimal("0.0010").multiply(ratio));
        assertThat(result.get(3)).isSameAs(post);
    }

    private YahooCandleDto candle(String iso, String open, String high, String low, String close) {
        return new YahooCandleDto(
                LocalDateTime.parse(iso),
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                0L);
    }
}
