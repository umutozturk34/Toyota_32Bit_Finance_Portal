package com.finance.portfolio.model;
import com.finance.common.model.MarketType;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AssetTypeTest {

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
