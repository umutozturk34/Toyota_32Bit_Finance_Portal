package com.finance.portfolio.model;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    @ParameterizedTest
    @CsvSource({
            "STOCK,     true",
            "FUND,      true",
            "CRYPTO,    false",
            "FOREX,     false",
            "COMMODITY, false",
            "VIOP,      false"
    })
    void isWholeUnitOnlyTrueOnlyForStockAndFund(AssetType assetType, boolean expected) {
        assertThat(assetType.isWholeUnitOnly()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "STOCK,     STOCK",
            "CRYPTO,    CRYPTO",
            "FOREX,     FOREX",
            "FUND,      FUND",
            "COMMODITY, COMMODITY",
            "VIOP,      VIOP"
    })
    void trackedAssetTypeResolvesPeer_forTrackedClasses(AssetType assetType, TrackedAssetType expected) {
        assertThat(assetType.trackedAssetType()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"DEPOSIT", "BOND"})
    void trackedAssetTypeIsNull_forFixedIncomeClasses(AssetType assetType) {
        // DEPOSIT/BOND map to a MarketType but have NO TrackedAssetType peer; the safe lookup returns null
        // instead of throwing, so the IllegalArgumentException foot-gun (valueOf("BOND"/"DEPOSIT")) is closed.
        assertThat(assetType.trackedAssetType()).isNull();
    }

    @ParameterizedTest
    @EnumSource(AssetType.class)
    void trackedAssetTypeNeverThrows_forAnyAssetType(AssetType assetType) {
        // The whole point of the guard: trackedAssetType() must never throw for ANY class — unlike
        // TrackedAssetType.valueOf(assetType.name()), which throws for DEPOSIT/BOND and would 500 the request.
        assertThatCode(assetType::trackedAssetType).doesNotThrowAnyException();
    }
}
