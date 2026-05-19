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

    @Override
    public void refreshAll() {
        Set<String> activeSymbols = refreshLiveSnapshots();
        deactivateStale(activeSymbols);
        enrichSpecs();
        enrichMissingPrices();
        sweepExpired();
        syncCandlesFromLastStored();
    }

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

    public Set<String> refreshLiveSnapshots() {
        List<ViopQuoteSnapshot> snapshots = marketData.fetchAllLiveSnapshots();
        if (snapshots == null || snapshots.isEmpty()) {
            log.info("VIOP live snapshots: upstream returned 0 rows (market closed or upstream stale)");
            return Set.of();
        }
        return entityWriter.applyBulkSnapshots(snapshots);
    }

    public int deactivateStale(Set<String> activeSymbols) {
        int deactivated = entityWriter.deactivateNotIn(activeSymbols);
        log.info("VIOP: {} non-trading contracts deactivated", deactivated);
        return deactivated;
    }

    public int enrichSpecs() {
        List<ViopContractSpec> futures = marketData.fetchFutureContractSpecs();
        int enriched = entityWriter.enrichSpecs(futures);
        log.info("VIOP: {} active contracts enriched with VadeliIslemler specs", enriched);
        return enriched;
    }

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

    public int sweepExpired() {
        return entityWriter.markExpired(LocalDate.now());
    }

    private boolean isLastPriceFromToday(com.finance.market.viop.model.ViopContract contract, LocalDate today) {
        java.time.LocalDateTime lastUpdated = contract.getLastUpdated();
        return lastUpdated != null && lastUpdated.toLocalDate().equals(today);
    }

    /**
     * Gap-fills VIOP candles from each contract's last stored candle through yesterday via the
     * upstream history endpoint, then syncs today's candle from the in-memory {@code lastPrice}
     * (no extra upstream call for today). Re-fetching candles we already have is intentionally
     * avoided — this is fill-since-last, not "refresh everything". On non-trading days
     * (weekend/holiday) upstream still serves the previous session's snapshot; those must NOT
     * be written under today's date, so today-sync requires the snapshot timestamp to be today.
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
