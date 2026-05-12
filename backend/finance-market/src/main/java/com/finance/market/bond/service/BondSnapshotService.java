package com.finance.market.bond.service;


import com.finance.market.bond.client.EvdsBondClient;
import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.external.BondSerieDto;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import com.finance.market.core.client.AbstractEvdsClient;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.dto.internal.EvdsSerieResponse;
import com.finance.common.exception.BusinessException;
import com.finance.market.bond.mapper.EvdsBondClientMapper;
import com.finance.shared.util.BatchUpdateRunner;
import com.finance.market.bond.util.BondSerieFilterUtil;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
public class BondSnapshotService {

    private static final DateTimeFormatter EVDS_DATE_FMT = AbstractEvdsClient.DATE_FMT;

    private final EvdsBondClient evdsClient;
    private final EvdsBondClientMapper clientMapper;
    private final int batchSize;

    public BondSnapshotService(EvdsBondClient evdsClient,
                               EvdsBondClientMapper clientMapper,
                               BondProperties bondProperties) {
        this.evdsClient = evdsClient;
        this.clientMapper = clientMapper;
        this.batchSize = bondProperties.getBatchSize();
    }

    public List<BondSerieDto> fetchAndFilterSeries() {
        log.info("Fetching bond serie list from EVDS");
        List<EvdsSerieResponse> allSeries = evdsClient.fetchBondSerieList();
        log.info("Fetched {} raw series from EVDS", allSeries.size());

        List<BondSerieDto> filtered = BondSerieFilterUtil.filter(allSeries);
        log.info("Filtered to {} unique active bonds (from {} raw series)", filtered.size(), allSeries.size());

        if (filtered.isEmpty()) {
            throw new BusinessException("No valid bond series found after filtering");
        }
        return filtered;
    }

    public List<BondSnapshotDto> fetchSnapshotData(List<BondSerieDto> bondSeries) {
        List<List<BondSerieDto>> batches = BondSerieFilterUtil.partition(bondSeries, batchSize);
        String today = LocalDate.now().format(EVDS_DATE_FMT);

        log.info("Fetching snapshot data: {} bonds in {} batches (batchSize={}) for date={}",
                bondSeries.size(), batches.size(), batchSize, today);

        List<BondSnapshotDto> allSnapshots = new ArrayList<>();
        List<BatchItem> indexedBatches = new ArrayList<>(batches.size());
        for (int i = 0; i < batches.size(); i++) {
            indexedBatches.add(new BatchItem(i + 1, batches.get(i)));
        }

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                indexedBatches,
                batchItem -> {
                    List<String> codes = new ArrayList<>();
                    for (BondSerieDto serie : batchItem.batch()) {
                        codes.add(serie.serieCode());
                        codes.add(BondSerieFilterUtil.toOranCode(serie.isin()));
                    }

                    log.debug("Snapshot batch {}/{}: {} bonds, {} codes",
                            batchItem.index(), batches.size(), batchItem.batch().size(), codes.size());

                    EvdsDataResponse response = evdsClient.fetchBondData(codes, today, today);
                    List<BondSnapshotDto> batchSnapshots = clientMapper.toSnapshotDtos(batchItem.batch(), response);
                    allSnapshots.addAll(batchSnapshots);

                    log.debug("Snapshot batch {}/{} returned {} snapshots",
                            batchItem.index(), batches.size(), batchSnapshots.size());
                },
                item -> "batch-" + item.index(),
                "bond snapshot",
                5,
                (item, e) -> log.error("Snapshot batch {}/{} failed: {}", item.index(), batches.size(), e.getMessage(), e),
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("EVDS circuit breaker OPEN, stopping snapshot fetch"));

        log.info("Snapshot fetch complete: {}/{} batches OK, {} DTOs total",
                result.successCount(), batches.size(), allSnapshots.size());
        return allSnapshots;
    }

    private record BatchItem(int index, List<BondSerieDto> batch) {
    }
}
