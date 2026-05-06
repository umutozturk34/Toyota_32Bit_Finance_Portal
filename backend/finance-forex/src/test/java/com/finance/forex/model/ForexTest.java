package com.finance.forex.model;
import com.finance.forex.model.Forex;

import com.finance.common.service.MarketSnapshotProcessor;

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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ForexTest {

    private static final int SCALE = 4;
    private static final BigDecimal SPREAD = new BigDecimal("0.01");

    @Test
    void applyYahooSnapshotSetsCurrentPriceAndSellingWithSpread() {
        Forex forex = Forex.builder().currencyCode("USD").build();

        forex.applyYahooSnapshot(new BigDecimal("38.0000"), new BigDecimal("37.5000"), null, null, null, null, SPREAD, SCALE);

        assertThat(forex.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("38.0000"));
        assertThat(forex.getSellingPrice()).isEqualByComparingTo(new BigDecimal("38.3800"));
    }

    @Test
    void applyYahooSnapshotCalculatesChangeFields() {
        Forex forex = Forex.builder().currencyCode("USD").build();

        forex.applyYahooSnapshot(new BigDecimal("38.0000"), new BigDecimal("37.5000"), null, null, null, null, SPREAD, SCALE);

        assertThat(forex.getChangeAmount()).isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(forex.getChangePercent()).isEqualByComparingTo(new BigDecimal("1.3333"));
    }

    @Test
    void applyYahooSnapshotNullMarketPriceDoesNothing() {
        Forex forex = Forex.builder().currencyCode("USD")
                .currentPrice(new BigDecimal("37.0000")).build();

        forex.applyYahooSnapshot(null, new BigDecimal("37.5000"), null, null, null, null, SPREAD, SCALE);

        assertThat(forex.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("37.0000"));
    }

    @Test
    void applyYahooSnapshotNullPreviousCloseNullsChangeFields() {
        Forex forex = Forex.builder().currencyCode("USD").build();

        forex.applyYahooSnapshot(new BigDecimal("38.0000"), null, null, null, null, null, SPREAD, SCALE);

        assertThat(forex.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("38.0000"));
        assertThat(forex.getChangeAmount()).isNull();
        assertThat(forex.getChangePercent()).isNull();
    }

    @Test
    void applyYahooSnapshotZeroPreviousCloseNullsChangeFields() {
        Forex forex = Forex.builder().currencyCode("EUR").build();

        forex.applyYahooSnapshot(new BigDecimal("41.0000"), BigDecimal.ZERO, null, null, null, null, SPREAD, SCALE);

        assertThat(forex.getChangeAmount()).isNull();
        assertThat(forex.getChangePercent()).isNull();
    }

    @Test
    void applySyntheticPriceSetsCurrentAndSellingPrice() {
        Forex forex = Forex.builder().currencyCode("EUR").build();

        forex.applySyntheticPrice(new BigDecimal("41.5000"), new BigDecimal("41.0000"),
                null, null, null, SPREAD, SCALE);

        assertThat(forex.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("41.5000"));
        assertThat(forex.getSellingPrice()).isEqualByComparingTo(new BigDecimal("41.9150"));
    }

    @Test
    void applySyntheticPriceCalculatesChangeFromPreviousClose() {
        Forex forex = Forex.builder().currencyCode("EUR").build();

        forex.applySyntheticPrice(new BigDecimal("41.5000"), new BigDecimal("40.0000"),
                null, null, null, SPREAD, SCALE);

        assertThat(forex.getChangeAmount()).isEqualByComparingTo(new BigDecimal("1.5000"));
        assertThat(forex.getChangePercent()).isEqualByComparingTo(new BigDecimal("3.7500"));
    }

    @Test
    void applySyntheticPriceNullPreviousCloseSkipsChangeFields() {
        Forex forex = Forex.builder().currencyCode("GBP")
                .changeAmount(new BigDecimal("0.5000"))
                .changePercent(new BigDecimal("1.0000"))
                .build();

        forex.applySyntheticPrice(new BigDecimal("48.0000"), null,
                null, null, null, SPREAD, SCALE);

        assertThat(forex.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("48.0000"));
        assertThat(forex.getChangeAmount()).isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(forex.getChangePercent()).isEqualByComparingTo(new BigDecimal("1.0000"));
    }

    @Test
    void applySyntheticPriceNullSyntheticPriceDoesNothing() {
        Forex forex = Forex.builder().currencyCode("CHF")
                .currentPrice(new BigDecimal("42.0000")).build();

        forex.applySyntheticPrice(null, new BigDecimal("41.0000"),
                null, null, null, SPREAD, SCALE);

        assertThat(forex.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("42.0000"));
    }

    @Test
    void applySyntheticPriceSetsOhlcFromCandle() {
        Forex forex = Forex.builder().currencyCode("EUR").build();

        forex.applySyntheticPrice(new BigDecimal("41.5000"), new BigDecimal("41.0000"),
                new BigDecimal("41.2000"), new BigDecimal("41.6000"), new BigDecimal("41.1000"),
                SPREAD, SCALE);

        assertThat(forex.getOpenPrice()).isEqualByComparingTo(new BigDecimal("41.2000"));
        assertThat(forex.getDayHigh()).isEqualByComparingTo(new BigDecimal("41.6000"));
        assertThat(forex.getDayLow()).isEqualByComparingTo(new BigDecimal("41.1000"));
    }

    @Test
    void applyYahooSnapshotNegativeChangePercent() {
        Forex forex = Forex.builder().currencyCode("USD").build();

        forex.applyYahooSnapshot(new BigDecimal("37.0000"), new BigDecimal("38.0000"), null, null, null, null, SPREAD, SCALE);

        assertThat(forex.getChangeAmount()).isEqualByComparingTo(new BigDecimal("-1.0000"));
        assertThat(forex.getChangePercent()).isEqualByComparingTo(new BigDecimal("-2.6316"));
    }

    @Test
    void applyYahooSnapshotSellingPriceScaledCorrectly() {
        Forex forex = Forex.builder().currencyCode("EUR").build();

        forex.applyYahooSnapshot(new BigDecimal("41.1234"), new BigDecimal("40.0000"), null, null, null, null, new BigDecimal("0.015"), SCALE);

        assertThat(forex.getSellingPrice()).isEqualByComparingTo(new BigDecimal("41.7403"));
        assertThat(forex.getSellingPrice().scale()).isEqualTo(SCALE);
    }
}
