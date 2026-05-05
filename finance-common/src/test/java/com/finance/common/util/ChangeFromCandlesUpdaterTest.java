package com.finance.common.util;

import com.finance.common.model.BaseAsset;
import com.finance.common.model.BaseCandle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeFromCandlesUpdaterTest {

    private static final int SCALE = 4;

    @Test
    void should_skipUpdate_when_existingChangePercentIsNonZero() {
        TestAsset asset = new TestAsset();
        asset.applyChange(new BigDecimal("100"), new BigDecimal("95"), SCALE);
        BigDecimal previousAmount = asset.getChangeAmount();
        List<BaseCandle> top2 = List.of(candle(new BigDecimal("110")), candle(new BigDecimal("80")));

        boolean changed = ChangeFromCandlesUpdater.applyFromTopTwoDescIfMissing(
                asset, new BigDecimal("110"), top2, SCALE);

        assertThat(changed).isFalse();
        assertThat(asset.getChangeAmount()).isEqualByComparingTo(previousAmount);
    }

    @Test
    void should_applyChange_when_existingPercentIsNull() {
        TestAsset asset = new TestAsset();
        List<BaseCandle> top2 = List.of(candle(new BigDecimal("105")), candle(new BigDecimal("100")));

        boolean changed = ChangeFromCandlesUpdater.applyFromTopTwoDescIfMissing(
                asset, new BigDecimal("105"), top2, SCALE);

        assertThat(changed).isTrue();
        assertThat(asset.getChangeAmount()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(asset.getChangePercent()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }

    @Test
    void should_applyChange_when_existingPercentIsZero() {
        TestAsset asset = new TestAsset();
        asset.applyChange(new BigDecimal("100"), new BigDecimal("100"), SCALE);
        List<BaseCandle> top2 = List.of(candle(new BigDecimal("104")), candle(new BigDecimal("100")));

        boolean changed = ChangeFromCandlesUpdater.applyFromTopTwoDescIfMissing(
                asset, new BigDecimal("104"), top2, SCALE);

        assertThat(changed).isTrue();
        assertThat(asset.getChangePercent()).isEqualByComparingTo(new BigDecimal("4.0000"));
    }

    @Test
    void should_skipUpdate_when_currentPriceIsNull() {
        TestAsset asset = new TestAsset();
        List<BaseCandle> top2 = List.of(candle(new BigDecimal("105")), candle(new BigDecimal("100")));

        boolean changed = ChangeFromCandlesUpdater.applyFromTopTwoDescIfMissing(
                asset, null, top2, SCALE);

        assertThat(changed).isFalse();
        assertThat(asset.getChangePercent()).isNull();
    }

    @Test
    void should_skipUpdate_when_fewerThanTwoCandles() {
        TestAsset asset = new TestAsset();
        List<BaseCandle> only1 = List.of(candle(new BigDecimal("105")));

        boolean changed = ChangeFromCandlesUpdater.applyFromTopTwoDescIfMissing(
                asset, new BigDecimal("105"), only1, SCALE);

        assertThat(changed).isFalse();
        assertThat(asset.getChangePercent()).isNull();
    }

    @Test
    void should_skipUpdate_when_previousClosesNullInCandle() {
        TestAsset asset = new TestAsset();
        List<BaseCandle> top2 = List.of(candle(new BigDecimal("105")), candle(null));

        boolean changed = ChangeFromCandlesUpdater.applyFromTopTwoDescIfMissing(
                asset, new BigDecimal("105"), top2, SCALE);

        assertThat(changed).isFalse();
        assertThat(asset.getChangePercent()).isNull();
    }

    @Test
    void should_skipUpdate_when_emptyCandleList() {
        TestAsset asset = new TestAsset();

        boolean changed = ChangeFromCandlesUpdater.applyFromTopTwoDescIfMissing(
                asset, new BigDecimal("105"), List.of(), SCALE);

        assertThat(changed).isFalse();
        assertThat(asset.getChangePercent()).isNull();
    }

    private static BaseCandle candle(BigDecimal close) {
        BaseCandle candle = new TestCandle();
        candle.setClose(close);
        return candle;
    }

    private static class TestAsset extends BaseAsset {
        @Override
        public void scaleFields(int scale) {}

        @Override
        public String getCode() { return "TEST"; }

        @Override
        public java.math.BigDecimal getPriceTry() { return null; }
    }

    private static class TestCandle extends BaseCandle {
    }
}
