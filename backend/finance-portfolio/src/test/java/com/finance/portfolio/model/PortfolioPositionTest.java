package com.finance.portfolio.model;
import com.finance.portfolio.model.PortfolioPosition;

import com.finance.portfolio.model.AssetType;



import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioPositionTest {

    @ParameterizedTest
    @CsvSource({
            "100,    50,    5000.0000",
            "0.5,    2400000, 1200000.0000",
            "1,      1,     1.0000"
    })
    void shouldComputeEntryValueAsPriceTimesQuantity_whenPositive(String qty, String price, String expected) {
        PortfolioPosition pos = lot(new BigDecimal(qty), new BigDecimal(price));

        BigDecimal entryValue = pos.entryValue();

        assertThat(entryValue).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void shouldComputeCurrentValueAsCurrentPriceTimesQuantity_whenPriceProvided() {
        PortfolioPosition pos = lot(new BigDecimal("10"), new BigDecimal("100"));

        BigDecimal currentValue = pos.currentValue(new BigDecimal("150"));

        assertThat(currentValue).isEqualByComparingTo(new BigDecimal("1500.0000"));
    }

    @Test
    void shouldReturnZeroCurrentValue_whenPriceIsNull() {
        PortfolioPosition pos = lot(new BigDecimal("10"), new BigDecimal("100"));

        BigDecimal currentValue = pos.currentValue(null);

        assertThat(currentValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldOverwriteAllLotFields_whenAllArgumentsProvided() {
        PortfolioPosition pos = lot(new BigDecimal("100"), new BigDecimal("40"));
        LocalDateTime newDate = LocalDateTime.of(2024, 1, 1, 12, 0);

        pos.updateLot(newDate, new BigDecimal("55"), new BigDecimal("200"));

        assertThat(pos.getEntryDate()).isEqualTo(newDate);
        assertThat(pos.getEntryPrice()).isEqualByComparingTo(new BigDecimal("55"));
        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("200"));
    }

    @Test
    void shouldKeepExistingFields_whenNullArgumentsProvided() {
        LocalDateTime original = LocalDateTime.of(2023, 6, 15, 9, 30);
        PortfolioPosition pos = PortfolioPosition.builder()
                .assetType(AssetType.STOCK).assetCode("THYAO.IS")
                .quantity(new BigDecimal("100"))
                .entryDate(original)
                .entryPrice(new BigDecimal("40"))
                .build();

        pos.updateLot(null, null, null);

        assertThat(pos.getEntryDate()).isEqualTo(original);
        assertThat(pos.getEntryPrice()).isEqualByComparingTo(new BigDecimal("40"));
        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void shouldUpdateOnlyProvidedFields_whenPartialArguments() {
        PortfolioPosition pos = lot(new BigDecimal("100"), new BigDecimal("40"));

        pos.updateLot(null, new BigDecimal("55"), null);

        assertThat(pos.getEntryPrice()).isEqualByComparingTo(new BigDecimal("55"));
        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void syncTransientsFromTrackedAsset_populatesAssetTypeAndCode_whenTrackedAssetPresent() {
        com.finance.common.model.TrackedAsset tracked = com.finance.common.model.TrackedAsset.builder()
                .assetType(com.finance.common.model.TrackedAssetType.STOCK)
                .assetCode("AKBNK")
                .build();
        PortfolioPosition pos = PortfolioPosition.builder().build();
        pos.setTrackedAsset(tracked);

        pos.syncTransientsFromTrackedAsset();

        assertThat(pos.getAssetType()).isEqualTo(AssetType.STOCK);
        assertThat(pos.getAssetCode()).isEqualTo("AKBNK");
    }

    @Test
    void syncTransientsFromTrackedAsset_noOps_whenTrackedAssetNull() {
        PortfolioPosition pos = PortfolioPosition.builder().build();

        pos.syncTransientsFromTrackedAsset();

        assertThat(pos.getAssetType()).isNull();
        assertThat(pos.getAssetCode()).isNull();
    }

    @Test
    void prePersist_setsTimestamps_whenNullOnEntry() {
        PortfolioPosition pos = PortfolioPosition.builder().build();

        pos.prePersist();

        assertThat(pos.getCreatedAt()).isNotNull();
        assertThat(pos.getUpdatedAt()).isNotNull();
    }

    @Test
    void prePersist_preservesExistingTimestamps() {
        LocalDateTime existing = LocalDateTime.of(2020, 1, 1, 0, 0);
        PortfolioPosition pos = PortfolioPosition.builder()
                .createdAt(existing).updatedAt(existing).build();

        pos.prePersist();

        assertThat(pos.getCreatedAt()).isEqualTo(existing);
        assertThat(pos.getUpdatedAt()).isEqualTo(existing);
    }

    @Test
    void preUpdate_refreshesUpdatedAtTimestamp() {
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        PortfolioPosition pos = PortfolioPosition.builder().updatedAt(original).build();

        pos.preUpdate();

        assertThat(pos.getUpdatedAt()).isAfter(original);
    }

    private PortfolioPosition lot(BigDecimal qty, BigDecimal price) {
        return PortfolioPosition.builder()
                .assetType(AssetType.STOCK).assetCode("THYAO.IS")
                .quantity(qty)
                .entryDate(LocalDateTime.now())
                .entryPrice(price)
                .build();
    }
}
