package com.finance.market.fund.service;
import com.finance.market.core.service.MarketRefresher;

import com.finance.market.core.cache.MarketCacheService;



import com.finance.market.fund.model.Fund;
import com.finance.common.model.MarketType;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.market.fund.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the full fund refresh: snapshots, bulk candle sync, change-percent recompute, then
 * returns/risk and allocation enrichment. Single-fund refresh runs the same steps for one code with
 * each enrichment isolated so a failure does not block the others.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class FundUpdateService implements MarketRefresher {

    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final MarketCacheService<Fund> fundCacheService;
    private final FundSnapshotProcessor snapshotProcessor;
    private final FundEntityWriter entityWriter;
    private final FundCandleBulkSyncService bulkSyncService;
    private final FundCandleIncrementalRefreshService incrementalRefreshService;
    private final FundDetailEnrichmentService detailEnrichmentService;

    @Override
    public MarketType getMarketType() {
        return MarketType.FUND;
    }

    /**
     * Runs the full fund refresh in order: snapshots, bulk candle sync, change-percent recompute,
     * returns/risk enrichment, then allocation enrichment as of today.
     */
    public void refreshAll() {
        long totalStart = System.currentTimeMillis();
        snapshotProcessor.refreshAll();
        bulkSyncService.refreshAllCandles();
        recomputeChangePercents();
        detailEnrichmentService.enrichReturnsAndRisk();
        detailEnrichmentService.enrichAllocations(LocalDate.now());
        log.info("[TIMING] Total fund update took {}s", (System.currentTimeMillis() - totalStart) / 1000);
    }

    /**
     * Refreshes one fund: snapshot, incremental candles, then detail/returns/allocation enrichment.
     * Each enrichment step is isolated so a failure in one is logged and does not block the others.
     */
    @Override
    public void refresh(String fundCode) {
        snapshotProcessor.refreshOne(fundCode);
        incrementalRefreshService.refresh(fundCode);
        try {
            detailEnrichmentService.enrichSingleFundDetails(fundCode);
        } catch (Exception e) {
            log.warn("Single-fund detail enrichment failed for {}: {}", fundCode, e.getMessage());
        }
        try {
            detailEnrichmentService.enrichReturnsAndRiskForFund(fundCode);
        } catch (Exception e) {
            log.warn("Single-fund returns enrichment failed for {}: {}", fundCode, e.getMessage());
        }
        try {
            detailEnrichmentService.enrichAllocationsForFund(fundCode, LocalDate.now());
        } catch (Exception e) {
            log.warn("Single-fund allocation enrichment failed for {}: {}", fundCode, e.getMessage());
        }
    }

    /** @return whether a fund with the given code is known */
    public boolean exists(String fundCode) {
        return snapshotProcessor.exists(fundCode);
    }

    private void recomputeChangePercents() {
        long start = System.currentTimeMillis();
        Map<String, LocalDateTime> latestCandleDates = fundCandleRepository.findCandleDateRangePerFund().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (LocalDateTime) row[2]));
        int updated = 0;
        for (Fund fund : fundRepository.findAll()) {
            LocalDateTime latestDate = latestCandleDates.get(fund.getFundCode());
            if (latestDate == null) continue;
            if (entityWriter.refreshChangePercent(fund, latestDate)) {
                fundCacheService.putSnapshot(fund.getFundCode(), fund);
                updated++;
            }
        }
        log.info("[TIMING] Fund change percent recompute: {} updated in {}ms",
                updated, System.currentTimeMillis() - start);
    }
}
