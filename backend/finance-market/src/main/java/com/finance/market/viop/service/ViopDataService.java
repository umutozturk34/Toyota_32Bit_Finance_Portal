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
        refreshAllCandles();
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
        Set<String> seen = entityWriter.applyBulkSnapshots(snapshots);
        log.info("VIOP live snapshots applied: {} actively-traded contracts", seen.size());
        return seen;
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

    public int refreshAllCandles() {
        // Strategy: historical gap-fill via upstream for past days, today's close synced from
        // each contract's already-updated last_price (no extra upstream call for today). Keeps
        // candle and live snapshot in lock-step throughout the trading day.
        List<com.finance.market.viop.model.ViopContract> active = contractRepository.findAll(
                (root, query, cb) -> cb.isTrue(root.get("active")));
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        int totalPersisted = 0;
        int contractsTouched = 0;
        for (com.finance.market.viop.model.ViopContract contract : active) {
            String symbol = contract.getSymbol();
            try {
                int historical = historyProvider.refreshCandlesUpTo(symbol, yesterday);
                int todaySync = contract.getLastPrice() != null
                        ? historyProvider.upsertTodayCandle(symbol, contract.getLastPrice())
                        : 0;
                int persisted = historical + todaySync;
                if (persisted > 0) {
                    totalPersisted += persisted;
                    contractsTouched++;
                }
            } catch (Exception e) {
                log.debug("VIOP candle refresh skipped for {}: {}", symbol, e.getMessage());
            }
        }
        log.info("VIOP candle refresh: {} candle writes across {}/{} active contracts",
                totalPersisted, contractsTouched, active.size());
        return totalPersisted;
    }
}
