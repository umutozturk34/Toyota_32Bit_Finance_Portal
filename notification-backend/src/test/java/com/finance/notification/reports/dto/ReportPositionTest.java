package com.finance.notification.reports.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPositionTest {

    @Test
    void should_returnTrue_when_viopPositionNameContainsKapali() {
        ReportPosition position = position("VIOP", "F_XU0301225", "XU030 1225 · KAPALI",
                null, null);

        boolean result = position.isClosed();

        assertThat(result).isTrue();
    }

    @Test
    void should_returnFalse_when_viopPositionNameMissingKapali() {
        ReportPosition position = position("VIOP", "F_XU0301225", "XU030 1225",
                null, null);

        boolean result = position.isClosed();

        assertThat(result).isFalse();
    }

    @Test
    void should_returnFalse_when_viopPositionNameIsNull() {
        ReportPosition position = position("VIOP", "F_XU030", null, null, null);

        boolean result = position.isClosed();

        assertThat(result).isFalse();
    }

    @Test
    void should_returnTrue_when_nonViopAssetHasExitDate() {
        LocalDateTime exit = LocalDateTime.of(2026, 1, 1, 0, 0);
        ReportPosition position = position("STOCK", "ASELS", "Aselsan", exit, null);

        boolean result = position.isClosed();

        assertThat(result).isTrue();
    }

    @Test
    void should_returnFalse_when_nonViopAssetHasNoExitDate() {
        ReportPosition position = position("STOCK", "ASELS", "Aselsan", null, null);

        boolean result = position.isClosed();

        assertThat(result).isFalse();
    }

    @Test
    void should_treatViopCaseInsensitive_when_checkingAssetType() {
        ReportPosition position = position("viop", "F_X", "X · KAPALI", null, null);

        boolean result = position.isClosed();

        assertThat(result).isTrue();
    }

    @Test
    void should_returnNull_when_assetNameIsNull() {
        ReportPosition position = position("STOCK", "ASELS", null, null, null);

        String result = position.displayName();

        assertThat(result).isNull();
    }

    @Test
    void should_returnNull_when_assetNameEqualsAssetCode() {
        ReportPosition position = position("STOCK", "ASELS", "ASELS", null, null);

        String result = position.displayName();

        assertThat(result).isNull();
    }

    @Test
    void should_returnAssetName_when_nameDiffersFromCode() {
        ReportPosition position = position("STOCK", "ASELS", "Aselsan Elektronik", null, null);

        String result = position.displayName();

        assertThat(result).isEqualTo("Aselsan Elektronik");
    }

    @Test
    void should_stripKapaliSuffix_when_assetNameEndsWithIt() {
        ReportPosition position = position("VIOP", "F_XU030", "XU030 1225 · KAPALI",
                null, null);

        String result = position.displayName();

        assertThat(result).isEqualTo("XU030 1225");
    }

    @Test
    void should_returnDash_when_displayQuantityCalledWithNull() {
        ReportPosition position = position("STOCK", "ASELS", "Aselsan", null, null);

        String result = position.displayQuantity();

        assertThat(result).isEqualTo("—");
    }

    @Test
    void should_stripTrailingZeros_when_quantityHasDecimals() {
        ReportPosition position = position("STOCK", "ASELS", "Aselsan", null,
                new BigDecimal("100.500"));

        String result = position.displayQuantity();

        assertThat(result).isEqualTo("100.5");
    }

    @Test
    void should_formatAsInteger_when_quantityScaleIsNegativeAfterStrip() {
        ReportPosition position = position("STOCK", "ASELS", "Aselsan", null,
                new BigDecimal("1000"));

        String result = position.displayQuantity();

        assertThat(result).isEqualTo("1000");
    }

    private ReportPosition position(String assetType, String assetCode, String assetName,
                                    LocalDateTime exitDate, BigDecimal quantity) {
        return new ReportPosition(
                1L,
                assetType,
                assetCode,
                assetName,
                quantity,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                BigDecimal.ONE,
                exitDate,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
