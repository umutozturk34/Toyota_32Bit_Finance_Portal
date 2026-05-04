package com.finance.portfolio.service;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.finance.portfolio.dto.response.BackfillStatusResponse;
import com.finance.portfolio.dto.response.BackfillStatusResponse.PendingAsset;
import com.finance.portfolio.model.AssetType;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Log4j2
@Component
public class PortfolioBackfillTracker {

    private record PortfolioState(Set<PendingAsset> pending, long since) {}

    private final Map<Long, PortfolioState> states = new ConcurrentHashMap<>();
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void start(Long portfolioId, AssetType assetType, String assetCode) {
        PendingAsset key = new PendingAsset(assetType.name(), assetCode);
        states.compute(portfolioId, (id, prev) -> {
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
        states.computeIfPresent(portfolioId, (id, prev) -> {
            prev.pending().remove(key);
            return prev.pending().isEmpty() ? null : prev;
        });
        broadcast(portfolioId);
    }

    public SseEmitter subscribe(Long portfolioId) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(portfolioId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> remove(portfolioId, emitter));
        emitter.onTimeout(() -> remove(portfolioId, emitter));
        emitter.onError((e) -> remove(portfolioId, emitter));
        send(emitter, snapshot(portfolioId));
        return emitter;
    }

    public BackfillStatusResponse snapshot(Long portfolioId) {
        PortfolioState state = states.get(portfolioId);
        if (state == null) return new BackfillStatusResponse(false, null, List.of());
        synchronized (state.pending()) {
            return new BackfillStatusResponse(true, state.since(), List.copyOf(state.pending()));
        }
    }

    private void remove(Long portfolioId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(portfolioId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) emitters.remove(portfolioId, list);
    }

    private void broadcast(Long portfolioId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(portfolioId);
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
