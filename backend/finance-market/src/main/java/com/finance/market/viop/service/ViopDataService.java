package com.finance.market.viop.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.MarketRefresher;
import com.finance.market.viop.dto.ViopContractSpec;
import com.finance.market.viop.dto.ViopQuoteSnapshot;
import com.finance.market.viop.port.ViopMarketDataPort;
import com.finance.market.viop.repository.ViopContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the VIOP refresh cycle: pull live snapshots, deactivate contracts that stopped
 * trading, enrich specs and missing prices, expire matured contracts, and sync daily candles.
 * Each step is resilient — failures for one symbol are logged and skipped, not propagated.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ViopDataService implements MarketRefresher {

    private final ViopMarketDataPort marketData;
    private final ViopEntityWriter entityWriter;
    private final ViopContractRepository contractRepository;
    private final ViopHistoryProvider historyProvider;

    @Override
    public MarketType getMarketType() {
        return MarketType.VIOP;
    }

    /**
     * Runs the full VIOP refresh pipeline in order: apply live snapshots, deactivate contracts that
     * no longer trade, enrich contract specs and any missing prices, expire matured contracts, and
     * sync daily candles. Each stage is independently resilient to per-symbol upstream failures.
     */
    @Override
    public void refreshAll() {
        Set<String> activeSymbols = refreshLiveSnapshots();
        deactivateStale(activeSymbols);
        enrichSpecs();
        enrichMissingPrices();
        sweepExpired();
        syncCandlesFromLastStored();
    }

    /**
     * Refreshes a single contract: applies its latest snapshot, then best-effort syncs its candles up
     * to today. A candle-refresh failure is logged at debug and swallowed so the snapshot still lands.
     */
    @Override
    public void refresh(String code) {
        ViopQuoteSnapshot snapshot = marketData.fetchSnapshot(code);
        entityWriter.applySnapshot(code, snapshot);
        try {
            historyProvider.refreshCandlesUpTo(code, LocalDate.now());
        } catch (Exception e) {
            log.debug("VIOP candle refresh skipped for {}: {}", code, e.getMessage());
        }
        log.info("VIOP contract refreshed: {}", code);
    }

    /** Applies all live snapshots and returns the symbols that traded; empty when the market is closed. */
    public Set<String> refreshLiveSnapshots() {
        List<ViopQuoteSnapshot> snapshots = marketData.fetchAllLiveSnapshots();
        if (snapshots == null || snapshots.isEmpty()) {
            log.info("VIOP live snapshots: upstream returned 0 rows (market closed or upstream stale)");
            return Set.of();
        }
        return entityWriter.applyBulkSnapshots(snapshots);
    }

    /**
     * Marks inactive every stored contract whose symbol is absent from the set that traded in this
     * cycle, pruning the active universe to what the market currently quotes.
     *
     * @param activeSymbols symbols that produced a live snapshot this cycle
     * @return the number of contracts deactivated
     */
    public int deactivateStale(Set<String> activeSymbols) {
        // Guard against a failed/empty bulk scrape: an empty set would deactivate the ENTIRE active universe.
        // Treat "no symbols this cycle" as a transient upstream gap and leave the universe untouched.
        if (activeSymbols == null || activeSymbols.isEmpty()) {
            log.warn("VIOP: live snapshot produced no symbols — skipping stale deactivation to avoid nuking the universe");
            return 0;
        }
        int deactivated = entityWriter.deactivateNotIn(activeSymbols);
        log.info("VIOP: {} non-trading contracts deactivated", deactivated);
        return deactivated;
    }

    /**
     * Augments active contracts with futures contract specifications fetched from the VadeliIslemler
     * feed (underlying, expiry, multipliers, etc.).
     *
     * @return the number of contracts whose specs were updated
     */
    public int enrichSpecs() {
        List<ViopContractSpec> futures = marketData.fetchFutureContractSpecs();
        int enriched = entityWriter.enrichSpecs(futures);
        log.info("VIOP: {} active contracts enriched with VadeliIslemler specs", enriched);
        return enriched;
    }

    /** Fills in prices for active contracts that have none yet via per-symbol snapshot fetches. */
    public int enrichMissingPrices() {
        List<String> symbols = contractRepository.findActiveSymbolsWithoutPrice();
        int enriched = 0;
        for (String symbol : symbols) {
            try {
                ViopQuoteSnapshot snap = marketData.fetchSnapshot(symbol);
                entityWriter.applySnapshot(symbol, snap);
                enriched++;
            } catch (Exception e) {
                log.debug("VIOP price enrichment skipped for {}: {}", symbol, e.getMessage());
            }
        }
        log.info("VIOP price enrichment: {}/{} contracts enriched via OneEndeks", enriched, symbols.size());
        return enriched;
    }

    /**
     * Marks as expired every contract whose maturity date is strictly before today — a contract is still
     * tradable on its own expiry day, so it is only flagged once that day has passed (query: {@code expiryDate < today}).
     *
     * @return the number of contracts newly flagged expired
     */
    public int sweepExpired() {
        return entityWriter.markExpired(LocalDate.now());
    }

    private boolean isLastPriceFromToday(com.finance.market.viop.model.ViopContract contract, LocalDate today) {
        java.time.LocalDateTime lastUpdated = contract.getLastUpdated();
        return lastUpdated != null && lastUpdated.toLocalDate().equals(today);
    }

    /**
     * Brings each active contract's candles up to yesterday, then adds today's candle only from a
     * snapshot whose last price is actually from today (stale snapshots are skipped, not back-dated).
     */
    public int syncCandlesFromLastStored() {
        List<com.finance.market.viop.model.ViopContract> active = contractRepository.findAll(
                (root, query, cb) -> cb.isTrue(root.get("active")));
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        int totalPersisted = 0;
        int contractsTouched = 0;
        int staleSkipped = 0;
        for (com.finance.market.viop.model.ViopContract contract : active) {
            String symbol = contract.getSymbol();
            try {
                int historical = historyProvider.refreshCandlesUpTo(symbol, yesterday);
                int todaySync = 0;
                if (contract.getLastPrice() != null && isLastPriceFromToday(contract, today)) {
                    todaySync = historyProvider.upsertTodayCandle(symbol, contract.getLastPrice());
                } else if (contract.getLastPrice() != null) {
                    staleSkipped++;
                }
                int persisted = historical + todaySync;
                if (persisted > 0) {
                    totalPersisted += persisted;
                    contractsTouched++;
                }
            } catch (Exception e) {
                log.debug("VIOP candle refresh skipped for {}: {}", symbol, e.getMessage());
            }
        }
        log.info("VIOP candle refresh: {} candle writes across {}/{} active contracts (today-sync skipped for {} stale snapshots)",
                totalPersisted, contractsTouched, active.size(), staleSkipped);
        return totalPersisted;
    }
}
