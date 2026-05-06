package com.finance.notification.alert.model;

import com.finance.common.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceAlertTest {

    private PriceAlert sample(boolean active, AlertDirection direction, BigDecimal threshold, BigDecimal reference) {
        return PriceAlert.builder()
                .userSub("user-1")
                .marketType(MarketType.CRYPTO)
                .assetCode("BTC")
                .direction(direction)
                .threshold(threshold)
                .currency("TRY")
                .referencePrice(reference)
                .active(active)
                .build();
    }

    @Test
    void evaluate_returnsTrueWhenAboveThreshold() {
        PriceAlert alert = sample(true, AlertDirection.ABOVE, BigDecimal.valueOf(100), null);

        boolean result = alert.evaluate(BigDecimal.valueOf(105));

        assertThat(result).isTrue();
    }

    @Test
    void evaluate_returnsFalseWhenInactive() {
        PriceAlert alert = sample(false, AlertDirection.ABOVE, BigDecimal.valueOf(100), null);

        boolean result = alert.evaluate(BigDecimal.valueOf(150));

        assertThat(result).isFalse();
    }

    @Test
    void evaluate_returnsFalseWhenAlreadyTriggered() {
        PriceAlert alert = sample(true, AlertDirection.ABOVE, BigDecimal.valueOf(100), null);
        alert.markFired();

        boolean result = alert.evaluate(BigDecimal.valueOf(150));

        assertThat(result).isFalse();
    }

    @Test
    void evaluate_returnsFalseWhenCurrentPriceNull() {
        PriceAlert alert = sample(true, AlertDirection.ABOVE, BigDecimal.valueOf(100), null);

        boolean result = alert.evaluate(null);

        assertThat(result).isFalse();
    }

    @Test
    void markFired_setsTimestampAndDeactivates() {
        PriceAlert alert = sample(true, AlertDirection.ABOVE, BigDecimal.valueOf(100), null);

        alert.markFired();

        assertThat(alert.getTriggeredAt()).isNotNull();
        assertThat(alert.isActive()).isFalse();
    }

    @Test
    void belongsTo_matchesOnlySameUserSub() {
        PriceAlert alert = sample(true, AlertDirection.ABOVE, BigDecimal.valueOf(100), null);

        assertThat(alert.belongsTo("user-1")).isTrue();
        assertThat(alert.belongsTo("intruder")).isFalse();
    }
}
