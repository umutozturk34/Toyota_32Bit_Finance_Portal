package com.finance.market.fund.service;
import com.finance.market.core.service.MarketRefresher;

import com.finance.market.core.cache.MarketCacheService;

import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.market.fund.model.Fund;
import com.finance.common.model.MarketType;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.market.fund.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Override
    public MarketType getMarketType() {
        return MarketType.FUND;
    }

    public void refreshAll() {
        long totalStart = System.currentTimeMillis();
        snapshotProcessor.refreshAll();
        bulkSyncService.refreshAllCandles();
        recomputeChangePercents();
        log.info("[TIMING] Total fund update took {}s", (System.currentTimeMillis() - totalStart) / 1000);
    }

    @Override
    public void refresh(String fundCode) {
        snapshotProcessor.refreshOne(fundCode);
        incrementalRefreshService.refresh(fundCode);
    }

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
