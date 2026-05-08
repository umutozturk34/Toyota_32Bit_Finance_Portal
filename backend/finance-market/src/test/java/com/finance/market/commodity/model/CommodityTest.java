package com.finance.market.commodity.model;
import com.finance.market.commodity.model.Commodity;

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

class CommodityTest {

    private static final int SCALE = 4;

    @Test
    void applyPriceSnapshotSetsTryAndUsdFields() {
        Commodity commodity = new Commodity();
        CommoditySnapshotInput input = new CommoditySnapshotInput(
                new BigDecimal("100000"),
                new BigDecimal("99000"),
                new BigDecimal("4000"),
                new BigDecimal("3960"),
                new BigDecimal("99500"),
                new BigDecimal("101000"),
                new BigDecimal("98500"),
                250000L);

        commodity.applyPriceSnapshot(input, SCALE);

        assertThat(commodity.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("100000.0000"));
        assertThat(commodity.getCurrentPriceUsd()).isEqualByComparingTo(new BigDecimal("4000.0000"));
        assertThat(commodity.getPreviousPriceUsd()).isEqualByComparingTo(new BigDecimal("3960.0000"));
        assertThat(commodity.getOpenPrice()).isEqualByComparingTo(new BigDecimal("99500.0000"));
        assertThat(commodity.getDayHigh()).isEqualByComparingTo(new BigDecimal("101000.0000"));
        assertThat(commodity.getDayLow()).isEqualByComparingTo(new BigDecimal("98500.0000"));
        assertThat(commodity.getVolume()).isEqualTo(250000L);
        assertThat(commodity.getYahooUpdatedAt()).isNotNull();
    }

    @Test
    void applyPriceSnapshotComputesChangeFields() {
        Commodity commodity = new Commodity();
        CommoditySnapshotInput input = new CommoditySnapshotInput(
                new BigDecimal("110"),
                new BigDecimal("100"),
                new BigDecimal("10"),
                new BigDecimal("9"),
                null, null, null, null);

        commodity.applyPriceSnapshot(input, SCALE);

        assertThat(commodity.getChangeAmount()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(commodity.getChangePercent()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void applyPriceSnapshotSkipsWhenTryPriceNull() {
        Commodity commodity = new Commodity();
        commodity.setCurrentPrice(new BigDecimal("999"));
        CommoditySnapshotInput input = new CommoditySnapshotInput(null, null, null, null, null, null, null, null);

        commodity.applyPriceSnapshot(input, SCALE);

        assertThat(commodity.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("999"));
    }

    @Test
    void applyPriceSnapshotWithoutUsdFieldsLeavesThemNull() {
        Commodity derivative = new Commodity();
        CommoditySnapshotInput input = new CommoditySnapshotInput(
                new BigDecimal("4500"), new BigDecimal("4450"),
                null, null,
                new BigDecimal("4480"), new BigDecimal("4520"), new BigDecimal("4440"), 1000L);

        derivative.applyPriceSnapshot(input, SCALE);

        assertThat(derivative.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("4500.0000"));
        assertThat(derivative.getCurrentPriceUsd()).isNull();
        assertThat(derivative.getPreviousPriceUsd()).isNull();
    }

    @Test
    void getCodeReturnsCommodityCode() {
        Commodity commodity = new Commodity();
        commodity.setCommodityCode("XAUTRYG");

        assertThat(commodity.getCode()).isEqualTo("XAUTRYG");
    }

    @Test
    void resolveDisplayNamePrefersTurkishName() {
        Commodity commodity = new Commodity();
        commodity.setCommodityCode("GOLD");
        commodity.setCommodityName("Gold");
        commodity.setCommodityNameTr("Altın");

        assertThat(commodity.resolveDisplayName()).isEqualTo("Altın");
    }

    @Test
    void resolveDisplayNameFallsBackToCodeWhenNoName() {
        Commodity commodity = new Commodity();
        commodity.setCommodityCode("WHEAT");

        assertThat(commodity.resolveDisplayName()).isEqualTo("WHEAT");
    }
}
