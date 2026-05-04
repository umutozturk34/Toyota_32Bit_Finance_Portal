package com.finance.portfolio.service;

import com.finance.portfolio.dto.response.BackfillStatusResponse;
import com.finance.portfolio.model.AssetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioBackfillTrackerTest {

    private PortfolioBackfillTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new PortfolioBackfillTracker();
    }

    @Test
    void start_buildsActiveStateWithPendingAsset() {
        tracker.start(1L, AssetType.CRYPTO, "BTC");

        BackfillStatusResponse snap = tracker.snapshot(1L);

        assertThat(snap.running()).isTrue();
        assertThat(snap.pendingAssets()).hasSize(1);
        assertThat(snap.pendingAssets().get(0).assetCode()).isEqualTo("BTC");
    }

    @Test
    void start_appendsToExistingState() {
        tracker.start(1L, AssetType.CRYPTO, "BTC");
        tracker.start(1L, AssetType.CRYPTO, "ETH");

        BackfillStatusResponse snap = tracker.snapshot(1L);

        assertThat(snap.pendingAssets()).hasSize(2);
    }

    @Test
    void finish_removesAssetFromPending() {
        tracker.start(1L, AssetType.CRYPTO, "BTC");
        tracker.start(1L, AssetType.CRYPTO, "ETH");

        tracker.finish(1L, AssetType.CRYPTO, "BTC");

        BackfillStatusResponse snap = tracker.snapshot(1L);
        assertThat(snap.pendingAssets()).hasSize(1);
        assertThat(snap.pendingAssets().get(0).assetCode()).isEqualTo("ETH");
    }

    @Test
    void finish_clearsStateWhenLastAssetCompletes() {
        tracker.start(1L, AssetType.CRYPTO, "BTC");

        tracker.finish(1L, AssetType.CRYPTO, "BTC");

        BackfillStatusResponse snap = tracker.snapshot(1L);
        assertThat(snap.running()).isFalse();
        assertThat(snap.pendingAssets()).isEmpty();
    }

    @Test
    void snapshot_returnsInactiveWhenNoStateRecorded() {
        BackfillStatusResponse snap = tracker.snapshot(99L);

        assertThat(snap.running()).isFalse();
        assertThat(snap.pendingAssets()).isEmpty();
    }

    @Test
    void subscribe_returnsEmitterAndAttachesToActiveState() {
        tracker.start(1L, AssetType.CRYPTO, "BTC");

        SseEmitter emitter = tracker.subscribe(1L);

        assertThat(emitter).isNotNull();
    }

    @Test
    void portfolioStatesAreIsolatedAcrossPortfolios() {
        tracker.start(1L, AssetType.CRYPTO, "BTC");
        tracker.start(2L, AssetType.STOCK, "AAPL");

        BackfillStatusResponse one = tracker.snapshot(1L);
        BackfillStatusResponse two = tracker.snapshot(2L);

        assertThat(one.pendingAssets().get(0).assetCode()).isEqualTo("BTC");
        assertThat(two.pendingAssets().get(0).assetCode()).isEqualTo("AAPL");
    }
}
