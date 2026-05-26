package com.finance.market.bond.service;


import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.external.BondSerieDto;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import com.finance.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class BondDataService {

    private final BondSnapshotService bondSnapshotService;
    private final BondRateHistoryService bondRateHistoryService;
    private final BondProperties bondProperties;

    public void updateBonds() {
        log.info("Starting unified bond update");

        List<BondSerieDto> bondSeries = bondSnapshotService.fetchAndFilterSeries();
        List<BondSnapshotDto> allSnapshots = bondSnapshotService.fetchSnapshotData(bondSeries);

        if (allSnapshots.isEmpty()) {
            throw new BusinessException("No bond snapshot data returned from EVDS");
        }

        LocalDateTime now = LocalDateTime.now();
        int chunkSize = Math.max(1, bondProperties.getHistoryBatchSize());
        int successBatches = 0;
        int failedBatches = 0;
        int processed = 0;
        for (int i = 0; i < allSnapshots.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, allSnapshots.size());
            List<BondSnapshotDto> chunk = allSnapshots.subList(i, end);
            try {
                bondRateHistoryService.processBatch(chunk, now);
                successBatches++;
                processed += chunk.size();
            } catch (Exception e) {
                failedBatches++;
                log.error("Bond batch {}-{} failed: {}", i, end, e.getMessage(), e);
            }
        }
        log.info("Bond update completed: {} batches OK, {} failed, {}/{} bonds processed",
                successBatches, failedBatches, processed, allSnapshots.size());
    }
}
