package com.finance.market.core.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarketProviderHelperTest {

    private static final Map<String, String> FIELDS = Map.of(
            "price", "currentPrice",
            "default", "marketCap");

    @Test
    void should_useDefaultFieldThenTiebreaker_when_sortKeyIsBlank() {
        // Arrange + Act — a blank sort (the "Varsayılan" state) must NOT yield Sort.unsorted() (arbitrary DB
        // order that shuffles on reload); it resolves the mapping's "default" field and appends the tiebreaker.
        Sort sort = MarketProviderHelper.buildSort("", null, FIELDS, "id");

        // Assert
        List<Sort.Order> orders = sort.toList();
        assertThat(orders).extracting(Sort.Order::getProperty).containsExactly("marketCap", "id");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void should_appendTiebreaker_when_sortingByAMappedField() {
        // Arrange + Act
        Sort sort = MarketProviderHelper.buildSort("price", "asc", FIELDS, "id");

        // Assert — primary field at the requested direction, stable tiebreaker ascending after it.
        List<Sort.Order> orders = sort.toList();
        assertThat(orders).extracting(Sort.Order::getProperty).containsExactly("currentPrice", "id");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void should_notDuplicateColumn_when_primaryFieldIsAlreadyTheTiebreaker() {
        // Arrange + Act
        Sort sort = MarketProviderHelper.buildSort("id", "asc", Map.of("id", "id"), "id");

        // Assert
        assertThat(sort.toList()).extracting(Sort.Order::getProperty).containsExactly("id");
    }

    @Test
    void should_fallBackToTiebreakerOnly_when_blankWithNoDefaultMapping() {
        // Arrange + Act — even with no "default" configured, a blank sort stays deterministic via the tiebreaker.
        Sort sort = MarketProviderHelper.buildSort(null, null, Map.of("price", "currentPrice"), "id");

        // Assert
        assertThat(sort.toList()).extracting(Sort.Order::getProperty).containsExactly("id");
    }
}
