package com.finance.notification.watchlist.model;

import com.finance.common.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistItemTest {

    private WatchlistItem itemWith(BigDecimal lastSeen, BigDecimal threshold) {
        return WatchlistItem.builder()
                .userSub("user-1")
                .marketType(MarketType.CRYPTO)
                .assetCode("BTC")
                .lastSeenPrice(lastSeen)
                .deltaThreshold(threshold)
                .build();
    }

    @Test
    void deltaPercent_isEmptyWhenLastSeenMissing() {
        WatchlistItem item = itemWith(null, null);

        Optional<BigDecimal> result = item.deltaPercent(BigDecimal.valueOf(105));

        assertThat(result).isEmpty();
    }

    @Test
    void deltaPercent_isEmptyWhenLastSeenZero() {
        WatchlistItem item = itemWith(BigDecimal.ZERO, null);

        Optional<BigDecimal> result = item.deltaPercent(BigDecimal.valueOf(105));

        assertThat(result).isEmpty();
    }

    @Test
    void deltaPercent_computesPositiveDelta() {
        WatchlistItem item = itemWith(BigDecimal.valueOf(100), null);

        Optional<BigDecimal> result = item.deltaPercent(BigDecimal.valueOf(105));

        assertThat(result).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("5.00"));
    }

    @Test
    void exceedsThreshold_usesGlobalDefaultWhenItemThresholdNull() {
        WatchlistItem item = itemWith(BigDecimal.valueOf(100), null);

        boolean atFive = item.exceedsThreshold(BigDecimal.valueOf(105), BigDecimal.valueOf(5));
        boolean belowFive = item.exceedsThreshold(BigDecimal.valueOf(104), BigDecimal.valueOf(5));

        assertThat(atFive).isTrue();
        assertThat(belowFive).isFalse();
    }

    @Test
    void exceedsThreshold_usesItemThresholdWhenSet() {
        WatchlistItem item = itemWith(BigDecimal.valueOf(100), BigDecimal.valueOf(2));

        boolean result = item.exceedsThreshold(BigDecimal.valueOf(103), BigDecimal.valueOf(20));

        assertThat(result).isTrue();
    }

    @Test
    void recordObservation_updatesLastSeenFields() {
        WatchlistItem item = itemWith(null, null);

        item.recordObservation(BigDecimal.valueOf(110));

        assertThat(item.getLastSeenPrice()).isEqualByComparingTo("110");
        assertThat(item.getLastSeenAt()).isNotNull();
    }

    @Test
    void belongsTo_matchesOnlySameUserSub() {
        WatchlistItem item = itemWith(null, null);

        assertThat(item.belongsTo("user-1")).isTrue();
        assertThat(item.belongsTo("intruder")).isFalse();
    }
}
