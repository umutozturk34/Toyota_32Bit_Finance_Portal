package com.finance.shared.util;

import com.finance.common.model.MarketType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnumDispatcherTest {

    @Test
    void should_indexValuesByEnumKey_when_keysAreDistinct() {
        // Arrange
        List<String> values = List.of("stock-handler", "crypto-handler");

        // Act
        Map<MarketType, String> map = EnumDispatcher.from(
                MarketType.class, values,
                v -> v.startsWith("stock") ? MarketType.STOCK : MarketType.CRYPTO);

        // Assert
        assertThat(map)
                .containsEntry(MarketType.STOCK, "stock-handler")
                .containsEntry(MarketType.CRYPTO, "crypto-handler");
    }

    @Test
    void should_keepLastValue_when_twoValuesShareSameKey() {
        // Arrange
        List<String> values = List.of("first", "second");

        // Act
        Map<MarketType, String> map = EnumDispatcher.from(
                MarketType.class, values, v -> MarketType.FOREX);

        // Assert
        assertThat(map).hasSize(1).containsEntry(MarketType.FOREX, "second");
    }

    @Test
    void should_returnEmptyMap_when_valuesEmpty() {
        // Arrange + Act
        Map<MarketType, String> map = EnumDispatcher.from(
                MarketType.class, List.of(), v -> MarketType.STOCK);

        // Assert
        assertThat(map).isEmpty();
    }
}
