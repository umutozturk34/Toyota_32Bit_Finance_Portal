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
        tracker = new PortfolioBackfillTracker(new com.finance.portfolio.config.PortfolioProperties());
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

    @Test
    void subscribe_sendsInitialSnapshotEventToEmitter() {
        tracker.start(1L, AssetType.CRYPTO, "BTC");

        SseEmitter emitter = tracker.subscribe(1L);

        assertThat(emitter).isNotNull();
    }

    @Test
    void start_broadcastsSnapshotToSubscribedEmitters() {
        tracker.subscribe(1L);

        tracker.start(1L, AssetType.CRYPTO, "BTC");

        BackfillStatusResponse snap = tracker.snapshot(1L);
        assertThat(snap.running()).isTrue();
        assertThat(snap.pendingAssets()).hasSize(1);
    }

    @Test
    void finish_broadcastsSnapshotToSubscribedEmitters() {
        tracker.start(1L, AssetType.CRYPTO, "BTC");
        tracker.subscribe(1L);

        tracker.finish(1L, AssetType.CRYPTO, "BTC");

        BackfillStatusResponse snap = tracker.snapshot(1L);
        assertThat(snap.running()).isFalse();
    }

    @Test
    void emitterCompletion_removesEmitterFromTracker() {
        SseEmitter emitter = tracker.subscribe(1L);

        invokeCompletionCallback(emitter);

        tracker.start(1L, AssetType.CRYPTO, "BTC");
        BackfillStatusResponse snap = tracker.snapshot(1L);
        assertThat(snap.running()).isTrue();
    }

    @Test
    void emitterTimeout_removesEmitterFromTracker() {
        SseEmitter emitter = tracker.subscribe(1L);

        invokeTimeoutCallback(emitter);

        tracker.start(1L, AssetType.CRYPTO, "BTC");
        BackfillStatusResponse snap = tracker.snapshot(1L);
        assertThat(snap.running()).isTrue();
    }

    @Test
    void emitterError_removesEmitterFromTracker() {
        SseEmitter emitter = tracker.subscribe(1L);

        invokeErrorCallback(emitter, new RuntimeException("boom"));

        tracker.start(1L, AssetType.CRYPTO, "BTC");
        BackfillStatusResponse snap = tracker.snapshot(1L);
        assertThat(snap.running()).isTrue();
    }

    @Test
    void broadcastSilentlyNoopsWhenNoEmittersSubscribed() {
        tracker.start(1L, AssetType.CRYPTO, "BTC");
        tracker.finish(1L, AssetType.CRYPTO, "BTC");

        BackfillStatusResponse snap = tracker.snapshot(1L);

        assertThat(snap.running()).isFalse();
    }

    @Test
    void start_swallowsIoException_whenEmitterSendFails() throws Exception {
        SseEmitter failingEmitter = new SseEmitter(0L) {
            @Override
            public void send(SseEventBuilder builder) throws java.io.IOException {
                throw new java.io.IOException("broken pipe");
            }
        };
        injectEmitter(tracker, 1L, failingEmitter);

        tracker.start(1L, AssetType.CRYPTO, "BTC");

        BackfillStatusResponse snap = tracker.snapshot(1L);
        assertThat(snap.running()).isTrue();
    }

    private static void invokeCompletionCallback(SseEmitter emitter) {
        runCallback(emitter, "completionCallback");
    }

    private static void invokeTimeoutCallback(SseEmitter emitter) {
        runCallback(emitter, "timeoutCallback");
    }

    private static void invokeErrorCallback(SseEmitter emitter, Throwable t) {
        try {
            java.lang.reflect.Field f = emitter.getClass().getSuperclass().getDeclaredField("errorCallback");
            f.setAccessible(true);
            Object holder = f.get(emitter);
            ((java.util.function.Consumer<Throwable>) holder).accept(t);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void runCallback(SseEmitter emitter, String fieldName) {
        try {
            java.lang.reflect.Field f = emitter.getClass().getSuperclass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object holder = f.get(emitter);
            ((Runnable) holder).run();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectEmitter(PortfolioBackfillTracker tracker, long portfolioId, SseEmitter emitter) throws Exception {
        java.lang.reflect.Field f = PortfolioBackfillTracker.class.getDeclaredField("emitters");
        f.setAccessible(true);
        com.github.benmanes.caffeine.cache.Cache<Long, java.util.concurrent.CopyOnWriteArrayList<SseEmitter>> cache =
                (com.github.benmanes.caffeine.cache.Cache<Long, java.util.concurrent.CopyOnWriteArrayList<SseEmitter>>) f.get(tracker);
        java.util.concurrent.CopyOnWriteArrayList<SseEmitter> list = cache.asMap()
                .computeIfAbsent(portfolioId, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        list.add(emitter);
    }
}
