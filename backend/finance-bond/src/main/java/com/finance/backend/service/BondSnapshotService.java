package com.finance.backend.service;

import com.finance.backend.client.EvdsClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.BondSerieDto;
import com.finance.backend.dto.external.BondSnapshotDto;
import com.finance.backend.dto.internal.EvdsBondDataResponse;
import com.finance.backend.dto.internal.EvdsBondSerieResponse;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.EvdsBondClientMapper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.BondSerieFilterUtil;
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

    private static final DateTimeFormatter EVDS_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final EvdsClient evdsClient;
    private final EvdsBondClientMapper clientMapper;
    private final int batchSize;

    public BondSnapshotService(EvdsClient evdsClient,
                               EvdsBondClientMapper clientMapper,
                               AppProperties appProperties) {
        this.evdsClient = evdsClient;
        this.clientMapper = clientMapper;
        this.batchSize = appProperties.getBond().getBatchSize();
    }

    public List<BondSerieDto> fetchAndFilterSeries() {
        log.info("Fetching bond serie list from EVDS");
        List<EvdsBondSerieResponse> allSeries = evdsClient.fetchBondSerieList();
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

                    EvdsBondDataResponse response = evdsClient.fetchBondData(codes, today, today);
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
