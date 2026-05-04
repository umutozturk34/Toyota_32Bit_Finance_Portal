package com.finance.portfolio.model;
import com.finance.common.model.MarketType;

import com.finance.portfolio.model.AssetType;

import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.common.config.CommissionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AssetTypeTest {

    private CommissionProperties commission;

    @BeforeEach
    void setUp() {
        commission = new CommissionProperties();
        commission.setStockRate(new BigDecimal("0.003"));
        commission.setCryptoRate(new BigDecimal("0.0025"));
        commission.setFundRate(new BigDecimal("0.0005"));
        commission.setCommodityRate(new BigDecimal("0.015"));
    }

    @ParameterizedTest
    @CsvSource({
            "STOCK,     0.003",
            "CRYPTO,    0.0025",
            "FUND,      0.0005",
            "COMMODITY, 0.015"
    })
    void commissionRateReadsConfiguredRateForTradableTypes(AssetType type, String expected) {
        BigDecimal rate = type.commissionRate(commission);

        assertThat(rate).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = AssetType.class, names = {"FOREX"})
    void commissionRateIsZeroForSpreadBasedTypes(AssetType type) {
        BigDecimal rate = type.commissionRate(commission);

        assertThat(rate).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @EnumSource(AssetType.class)
    void commissionRateReflectsLiveConfigChanges(AssetType type) {
        commission.setStockRate(new BigDecimal("1"));
        commission.setCryptoRate(new BigDecimal("1"));
        commission.setFundRate(new BigDecimal("1"));
        commission.setCommodityRate(new BigDecimal("1"));

        BigDecimal rate = type.commissionRate(commission);

        if (type == AssetType.FOREX) {
            assertThat(rate).isEqualByComparingTo(BigDecimal.ZERO);
        } else {
            assertThat(rate).isEqualByComparingTo("1");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "STOCK,     STOCK",
            "CRYPTO,    CRYPTO",
            "FOREX,     FOREX",
            "FUND,      FUND",
            "COMMODITY, COMMODITY"
    })
    void marketTypeMapsOneToOneWithAssetType(AssetType assetType, MarketType expected) {
        assertThat(assetType.marketType()).isEqualTo(expected);
    }
}
