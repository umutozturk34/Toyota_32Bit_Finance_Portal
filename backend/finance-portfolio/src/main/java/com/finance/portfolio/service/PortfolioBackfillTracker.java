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
                .maximumSize(1_000)
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

    public void finish(Long portfolioId, AssetType assetType, String assetCode) {
        PendingAsset key = new PendingAsset(assetType.name(), assetCode);
        states.asMap().computeIfPresent(portfolioId, (id, prev) -> {
            prev.pending().remove(key);
            return prev.pending().isEmpty() ? null : prev;
        });
        broadcast(portfolioId);
    }

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
            send(emitter, snap);
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
