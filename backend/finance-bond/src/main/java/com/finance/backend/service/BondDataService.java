package com.finance.backend.service;

import com.finance.backend.dto.external.BondSerieDto;
import com.finance.backend.dto.external.BondSnapshotDto;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
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

    public void updateBonds() {
        log.info("Starting unified bond update");

        List<BondSerieDto> bondSeries = bondSnapshotService.fetchAndFilterSeries();
        List<BondSnapshotDto> allSnapshots = bondSnapshotService.fetchSnapshotData(bondSeries);

        if (allSnapshots.isEmpty()) {
            throw new BusinessException("No bond snapshot data returned from EVDS");
        }

        LocalDateTime now = LocalDateTime.now();
        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                allSnapshots,
                dto -> bondRateHistoryService.processSingleBond(dto, now),
                BondSnapshotDto::seriesCode,
                "bond update",
                5,
                (dto, e) -> log.error("Failed to process bond {}: {}", dto.seriesCode(), e.getMessage(), e),
                null,
                null);

        BatchLogHelper.logSummaryWithTotal(log, "Bond update completed", result, allSnapshots.size());
    }
}
