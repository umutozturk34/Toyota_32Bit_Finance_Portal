package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.dto.response.ScenarioSeries;
import com.finance.app.analytics.service.InflationBeaterUniverseBuilder.CuratedAsset;
import com.finance.common.model.Currency;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulates the curated universe's per-asset return and caches it by (period, comparison currency). A
 * universe asset's return depends only on the window and the framing currency — never on which benchmark it
 * is compared against — so the ~2700-asset universe is simulated ONCE per (period, currency) and reused
 * across every benchmark of that currency. This caching concern is separated from the ranking orchestration
 * in {@link InflationBeaterService}: it owns nothing but the simulation cache and the lightweight projection,
 * and depends only on {@link ScenarioService}.
 *
 * <p>Caching the LIGHTWEIGHT per-asset summary (not the heavy daily {@link ScenarioResponse} series) keeps
 * this to a few MB. The 15-min TTL spans a warm run; the daily warm clears it first (via {@link #invalidate()})
 * so it always reflects the evening refresh. It holds 6 periods × 3 currencies = 18 entries.
 *
 * <p>A Spring-managed (singleton) collaborator injected into {@link InflationBeaterService}; the
 * singleton scope is what makes the cache shared across requests.
 */
@Component
public class InflationBeaterUniverseSimulator {

    private static final BigDecimal NOTIONAL_AMOUNT = new BigDecimal("10000");

    private final ScenarioService scenarioService;

    private final Cache<String, List<UniverseReturn>> universeCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(64)
            .build();

    public InflationBeaterUniverseSimulator(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    /** Lightweight projection of a simulated universe asset — its nominal return and completeness. */
    public record UniverseReturn(AnalyticsInstrumentType type, String code,
                                 BigDecimal nominalReturnPct, boolean partial) {}

    /**
     * Universe per-asset returns for (period, currency), cached so every benchmark of that currency reuses
     * one simulation. The heavy {@link ScenarioResponse} (full daily series) is projected to a lightweight
     * summary and discarded; only the per-asset return + partial flag are kept.
     */
    public List<UniverseReturn> returnsCached(String period, LocalDate startDate, LocalDate endDate,
                                              Currency currency, List<CuratedAsset> universe) {
        return universeCache.get(period + "|" + currency.name(),
                k -> simulateUniverse(startDate, endDate, currency, universe));
    }

    private List<UniverseReturn> simulateUniverse(LocalDate startDate, LocalDate endDate,
                                                  Currency currency, List<CuratedAsset> universe) {
        List<AnalyticsInstrument> instruments = new ArrayList<>(universe.size());
        for (CuratedAsset c : universe) {
            instruments.add(new AnalyticsInstrument(c.type(), c.code()));
        }
        ScenarioResponse scenario = scenarioService.simulate(
                new ScenarioRequest(NOTIONAL_AMOUNT, startDate, endDate, instruments, currency));
        List<UniverseReturn> out = new ArrayList<>(scenario.series().size());
        for (ScenarioSeries s : scenario.series()) {
            out.add(new UniverseReturn(s.instrument().type(), s.instrument().code(),
                    s.nominalReturnPct(), s.partial()));
        }
        return out;
    }

    /** Drops every cached per-(period,currency) universe simulation, forcing a fresh recompute. */
    public void invalidate() {
        universeCache.invalidateAll();
    }
}
