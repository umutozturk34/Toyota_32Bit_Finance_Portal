package com.finance.notification.alert.model;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the JPA lifecycle callbacks and re-arm transition that the higher-level
 * {@link PriceAlertTest} does not exercise: transient projection on load, defaulting on persist,
 * and reactivation of a fired alert.
 */
class PriceAlertLifecycleTest {

    @Test
    void should_projectMarketTypeAndCode_when_loadedWithTrackedAsset() {
        TrackedAsset tracked = TrackedAsset.builder()
                .id(7L).assetType(TrackedAssetType.CRYPTO).assetCode("btc").build();
        PriceAlert alert = PriceAlert.builder().userSub("user-1").trackedAsset(tracked).build();

        alert.syncTransientsFromTrackedAsset();

        assertThat(alert.getMarketType()).isEqualTo(MarketType.CRYPTO);
        assertThat(alert.getAssetCode()).isEqualTo("btc");
    }

    @Test
    void should_leaveTransientsNull_when_loadedWithoutTrackedAsset() {
        PriceAlert alert = PriceAlert.builder().userSub("user-1").build();

        alert.syncTransientsFromTrackedAsset();

        assertThat(alert.getMarketType()).isNull();
        assertThat(alert.getAssetCode()).isNull();
    }

    @Test
    void should_defaultCreatedAtAndCurrency_when_prePersistRunsWithoutValues() {
        PriceAlert alert = PriceAlert.builder().userSub("user-1").build();

        alert.prePersist();

        assertThat(alert.getCreatedAt()).isNotNull();
        assertThat(alert.getCurrency()).isEqualTo("TRY");
    }

    @Test
    void should_preserveProvidedCurrency_when_prePersistRunsWithBlankFallbackAvoided() {
        PriceAlert alert = PriceAlert.builder().userSub("user-1").currency("USD").build();

        alert.prePersist();

        assertThat(alert.getCurrency()).isEqualTo("USD");
    }

    @Test
    void should_clearTriggeredStateAndActivate_when_reactivated() {
        PriceAlert alert = PriceAlert.builder()
                .userSub("user-1").direction(AlertDirection.ABOVE)
                .threshold(BigDecimal.valueOf(100)).active(true).build();
        alert.markFired();

        alert.reactivate();

        assertThat(alert.getTriggeredAt()).isNull();
        assertThat(alert.isActive()).isTrue();
        assertThat(alert.shouldEvaluate()).isTrue();
    }
}
