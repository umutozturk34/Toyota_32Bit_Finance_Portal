package com.finance.portfolio.service;

import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.dto.response.BackfillStatusResponse;
import com.finance.portfolio.dto.response.BackfillStatusResponse.PendingAsset;
import com.finance.portfolio.model.AssetType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks in-progress snapshot recomputation per portfolio and pushes live status to the UI over SSE.
 * Holds the set of pending assets (Caffeine-cached, evicted after a TTL) and broadcasts a
 * {@link BackfillStatusResponse} to subscribed emitters whenever a backfill starts or finishes.
 */
@Log4j2
@Component
public class PortfolioBackfillTracker {

    private record PortfolioState(Set<PendingAsset> pending, long since) {}

    private final Cache<Long, PortfolioState> states;
    private final Cache<Long, CopyOnWriteArrayList<SseEmitter>> emitters;

    public PortfolioBackfillTracker(PortfolioProperties props) {
        PortfolioProperties.Backfill cfg = props.getBackfill();
        this.states = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(cfg.getStateCacheExpiryHours()))
                .maximumSize(cfg.getStateCacheMaxSize())
                .removalListener((Long id, PortfolioState state, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (cause.wasEvicted() && state != null) {
                        log.warn("Backfill state evicted portfolioId={} pending={} cause={}",
                                id, state.pending().size(), cause);
                    }
                })
                .build();
        this.emitters = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(cfg.getEmitterCacheExpiryMinutes()))
                .maximumSize(cfg.getEmittersCacheMaxSize())
                .removalListener((Long id, CopyOnWriteArrayList<SseEmitter> list, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (cause.wasEvicted() && list != null) {
                        log.warn("Backfill emitters evicted portfolioId={} count={} cause={}",
                                id, list.size(), cause);
                        list.forEach(SseEmitter::complete);
                    }
                })
                .build();
    }

    /** Marks an asset as backfilling and broadcasts the updated pending set to subscribers. */
    public void start(Long portfolioId, AssetType assetType, String assetCode) {
        PendingAsset key = new PendingAsset(assetType.name(), assetCode);
        states.asMap().compute(portfolioId, (id, prev) -> {
            if (prev == null) {
                Set<PendingAsset> set = Collections.synchronizedSet(new LinkedHashSet<>());
                set.add(key);
                return new PortfolioState(set, Instant.now().toEpochMilli());
            }
            prev.pending().add(key);
            return prev;
        });
        broadcast(portfolioId);
    }

    /** Clears an asset from the pending set (dropping portfolio state when none remain) and broadcasts. */
    public void finish(Long portfolioId, AssetType assetType, String assetCode) {
        PendingAsset key = new PendingAsset(assetType.name(), assetCode);
        states.asMap().computeIfPresent(portfolioId, (id, prev) -> {
            prev.pending().remove(key);
            return prev.pending().isEmpty() ? null : prev;
        });
        broadcast(portfolioId);
    }

    /** Registers a never-timing-out SSE emitter for the portfolio and immediately sends the current status. */
    public SseEmitter subscribe(Long portfolioId) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> list = emitters.asMap()
                .computeIfAbsent(portfolioId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> remove(portfolioId, emitter));
        emitter.onTimeout(() -> remove(portfolioId, emitter));
        emitter.onError((e) -> remove(portfolioId, emitter));
        send(emitter, snapshot(portfolioId));
        return emitter;
    }

    /** Current backfill status for the portfolio: running flag, start time and the pending-asset list. */
    public BackfillStatusResponse snapshot(Long portfolioId) {
        PortfolioState state = states.getIfPresent(portfolioId);
        if (state == null) return new BackfillStatusResponse(false, null, List.of());
        synchronized (state.pending()) {
            return new BackfillStatusResponse(true, state.since(), List.copyOf(state.pending()));
        }
    }

    private void remove(Long portfolioId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.getIfPresent(portfolioId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) emitters.asMap().remove(portfolioId, list);
    }

    private void broadcast(Long portfolioId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.getIfPresent(portfolioId);
        if (list == null) return;
        BackfillStatusResponse snap = snapshot(portfolioId);
        for (SseEmitter emitter : list) {
            try {
                send(emitter, snap);
            } catch (RuntimeException ex) {
                // A stale/already-completed emitter (completeWithError can re-throw) must not abort
                // delivery to the remaining subscribers — drop it and keep broadcasting the terminal
                // running:false event, otherwise the client's "preparing data" banner hangs.
                remove(portfolioId, emitter);
            }
        }
    }

    private static void send(SseEmitter emitter, BackfillStatusResponse snapshot) {
        try {
            emitter.send(SseEmitter.event().name("backfill-status").data(snapshot));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
