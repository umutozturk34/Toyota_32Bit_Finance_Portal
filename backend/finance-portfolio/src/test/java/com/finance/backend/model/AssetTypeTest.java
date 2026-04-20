package com.finance.backend.model;

import com.finance.backend.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AssetTypeTest {

    private AppProperties.Commission commission;

    @BeforeEach
    void setUp() {
        commission = new AppProperties.Commission();
        commission.setStockRate(new BigDecimal("0.003"));
        commission.setCryptoRate(new BigDecimal("0.0025"));
        commission.setFundRate(new BigDecimal("0.0005"));
    }

    @ParameterizedTest
    @CsvSource({
            "STOCK,  0.003",
            "CRYPTO, 0.0025",
            "FUND,   0.0005"
    })
    void commissionRateReadsConfiguredRateForTradableTypes(AssetType type, String expected) {
        BigDecimal rate = type.commissionRate(commission);

        assertThat(rate).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = AssetType.class, names = {"FOREX"})
    void commissionRateIsZeroForNonTradableTypes(AssetType type) {
        BigDecimal rate = type.commissionRate(commission);

        assertThat(rate).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @EnumSource(AssetType.class)
    void commissionRateReflectsLiveConfigChanges(AssetType type) {
        commission.setStockRate(new BigDecimal("1"));
        commission.setCryptoRate(new BigDecimal("1"));
        commission.setFundRate(new BigDecimal("1"));

        BigDecimal rate = type.commissionRate(commission);

        if (type == AssetType.FOREX) {
            assertThat(rate).isEqualByComparingTo(BigDecimal.ZERO);
        } else {
            assertThat(rate).isEqualByComparingTo("1");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "STOCK,  STOCK",
            "CRYPTO, CRYPTO",
            "FOREX,  FOREX",
            "FUND,   FUND"
    })
    void marketTypeMapsOneToOneWithAssetType(AssetType assetType, MarketType expected) {
        assertThat(assetType.marketType()).isEqualTo(expected);
    }
}
