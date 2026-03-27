package com.finance.backend.service;

import com.finance.backend.client.EvdsClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.BondRateItemDto;
import com.finance.backend.dto.external.BondSerieDto;
import com.finance.backend.dto.external.BondSnapshotDto;
import com.finance.backend.dto.internal.EvdsBondDataResponse;
import com.finance.backend.dto.internal.EvdsBondSerieResponse;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.BondMapper;
import com.finance.backend.mapper.EvdsBondClientMapper;
import com.finance.backend.model.Bond;
import com.finance.backend.model.BondRateHistory;
import com.finance.backend.repository.BondRateHistoryRepository;
import com.finance.backend.repository.BondRepository;
import com.finance.backend.util.BatchFailureGuard;
import com.finance.backend.util.BondSerieFilterUtil;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Log4j2
@Service
public class BondDataService {

    private static final DateTimeFormatter EVDS_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final EvdsClient evdsClient;
    private final EvdsBondClientMapper clientMapper;
    private final BondMapper bondMapper;
    private final BondRepository bondRepository;
    private final BondRateHistoryRepository rateHistoryRepository;
    private final MarketCacheService<Bond, BondRateHistory> bondCacheService;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int maxDaysPerRequest;
    private final java.math.BigDecimal rateThreshold;
    private final java.math.BigDecimal auctionThreshold;
    private final java.math.BigDecimal cpiFixedThreshold;
    private final java.math.BigDecimal faceValue;
    private final int daysInYear;

    public BondDataService(EvdsClient evdsClient,
            EvdsBondClientMapper clientMapper,
            BondMapper bondMapper,
            BondRepository bondRepository,
            BondRateHistoryRepository rateHistoryRepository,
            MarketCacheService<Bond, BondRateHistory> bondCacheService,
            PlatformTransactionManager transactionManager,
            AppProperties appProperties) {
        this.evdsClient = evdsClient;
        this.clientMapper = clientMapper;
        this.bondMapper = bondMapper;
        this.bondRepository = bondRepository;
        this.rateHistoryRepository = rateHistoryRepository;
        this.bondCacheService = bondCacheService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = appProperties.getBond().getBatchSize();
        this.maxDaysPerRequest = appProperties.getBond().getMaxDaysPerRequest();
        this.rateThreshold = appProperties.getBond().getRateThreshold();
        this.auctionThreshold = appProperties.getBond().getAuctionThreshold();
        this.cpiFixedThreshold = appProperties.getBond().getCpiFixedThreshold();
        this.faceValue = appProperties.getBond().getFaceValue();
        this.daysInYear = appProperties.getBond().getDaysInYear();
    }

    public void updateBonds() {
        log.info("Starting unified bond update");

        List<BondSerieDto> bondSeries = fetchAndFilterSeries();
        List<BondSnapshotDto> allSnapshots = fetchSnapshotData(bondSeries);

        if (allSnapshots.isEmpty()) {
            throw new BusinessException("No bond snapshot data returned from EVDS");
        }

        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        int failed = 0;
        List<String> failedCodes = new ArrayList<>();

        for (BondSnapshotDto dto : allSnapshots) {
            try {
                processSingleBond(dto, now);
                success++;
            } catch (Exception e) {
                failed++;
                failedCodes.add(dto.seriesCode());
                log.error("Failed to process bond {}: {}", dto.seriesCode(), e.getMessage(), e);
                BatchFailureGuard.check(success, failed, failedCodes, "bond update");
            }
        }

        log.info("Bond update completed: {} success, {} failed out of {} total", success, failed, allSnapshots.size());
        if (!failedCodes.isEmpty()) {
            log.warn("Failed bond codes: {}", failedCodes);
        }
    }

    private void processSingleBond(BondSnapshotDto dto, LocalDateTime now) {
        Optional<Bond> existingOpt = bondRepository.findById(dto.seriesCode());
        boolean isNew = existingOpt.isEmpty();

        final Bond bond;
        if (isNew) {
            bond = bondMapper.toEntity(dto, now);
            bond.setIssuer("HAZİNE");
            bond.resolveNextCouponDate();
        } else {
            bond = existingOpt.get();
            bondMapper.updateEntity(bond, dto, now);
            bond.resolveNextCouponDate();
        }

        BondSerieFilterUtil.sanitizeCouponRate(bond, dto);

        Bond savedBond = transactionTemplate.execute(status -> bondRepository.save(bond));

        boolean zeroCoupon = savedBond.getCouponRate() == null
                || savedBond.getCouponRate().compareTo(BigDecimal.ZERO) == 0;

        List<BondRateHistory> newRateRecords;
        if (zeroCoupon) {
            newRateRecords = List.of();
            log.debug("{} — zero coupon, skipping rate history", dto.isinCode());
        } else {
            newRateRecords = buildRateRecords(dto, savedBond);
        }

        if (!newRateRecords.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> rateHistoryRepository.saveAll(newRateRecords));
        }

        List<BondRateHistory> fullHistory = rateHistoryRepository.findByIsinCodeOrderByRateDateAsc(dto.isinCode());
        savedBond.resolveType(fullHistory, rateThreshold, auctionThreshold, cpiFixedThreshold);
        savedBond.resolveSimpleYield(faceValue, daysInYear);

        if (savedBond.isDiscounted()) {
            savedBond.setNextCouponDate(null);
        }

        transactionTemplate.executeWithoutResult(status -> bondRepository.save(savedBond));
        bondCacheService.putSnapshot(savedBond.getSeriesCode(), savedBond);
        bondCacheService.refreshHistory(dto.isinCode());

        log.info("Bond {} processed: type={}, yield={}, rateHistory={} records, newRates={}, new={}, zeroCoupon={}",
                dto.isinCode(), savedBond.getBondType(), savedBond.getSimpleYield(),
                fullHistory.size(), newRateRecords.size(), isNew, zeroCoupon);
    }

    private List<BondRateHistory> buildRateRecords(BondSnapshotDto dto, Bond bond) {
        String isinCode = dto.isinCode();
        LocalDate today = LocalDate.now();

        Optional<BondRateHistory> latestOpt = rateHistoryRepository.findTopByIsinCodeOrderByRateDateDesc(isinCode);

        if (latestOpt.isEmpty()) {
            if (dto.maturityStart() == null) {
                throw new BusinessException("Bond " + isinCode + " has no maturityStart, cannot fetch rate history");
            }
            LocalDate startDate = dto.maturityStart();
            log.debug("{} — no history, full fetch from {}", isinCode, startDate);

            List<BondRateHistory> fetched = fetchRateHistory(isinCode, bond, startDate, today);
            addTodayFromSnapshotIfMissing(fetched, dto, bond, today);
            return fetched;
        }

        LocalDate lastDate = latestOpt.get().getRateDate();
        long gapDays = ChronoUnit.DAYS.between(lastDate, today);

        if (gapDays <= 1) {
            log.debug("{} — snapshot only: lastDate={}, gapDays={}", isinCode, lastDate, gapDays);
            List<BondRateHistory> result = new ArrayList<>();
            addTodayFromSnapshotIfMissing(result, dto, bond, today);
            return result;
        }

        LocalDate fetchStart = lastDate.plusDays(1);
        log.debug("{} — incremental fetch: lastDate={}, gapDays={}, fetchStart={}",
                isinCode, lastDate, gapDays, fetchStart);

        List<BondRateHistory> fetched = fetchRateHistory(isinCode, bond, fetchStart, today);
        addTodayFromSnapshotIfMissing(fetched, dto, bond, today);
        return fetched;
    }

    private void addTodayFromSnapshotIfMissing(List<BondRateHistory> records, BondSnapshotDto dto, Bond bond,
            LocalDate today) {
        if (dto.couponRate() == null)
            return;

        boolean todayExists = records.stream().anyMatch(r -> today.equals(r.getRateDate()));
        if (todayExists)
            return;

        if (rateHistoryRepository.existsByIsinCodeAndRateDate(dto.isinCode(), today))
            return;

        records.add(BondRateHistory.builder()
                .bond(bond)
                .rateDate(today)
                .couponRate(dto.couponRate())
                .build());
    }

    private List<BondRateHistory> fetchRateHistory(String isinCode, Bond bond, LocalDate startDate, LocalDate endDate) {
        String oranCode = BondSerieFilterUtil.toOranCode(isinCode);

        if (!startDate.isBefore(endDate)) {
            log.debug("Rate history for {} is up to date (start={} >= end={}), skipping", isinCode, startDate, endDate);
            return new ArrayList<>();
        }

        List<LocalDate[]> windows = BondSerieFilterUtil.buildWindows(startDate, endDate, maxDaysPerRequest);
        log.debug("Fetching rate history for {}: {} to {} ({} windows)", isinCode, startDate, endDate, windows.size());

        List<BondRateHistory> allRecords = new ArrayList<>();
        int successWindows = 0;
        int failedWindows = 0;

        for (int i = 0; i < windows.size(); i++) {
            LocalDate[] window = windows.get(i);
            try {
                String start = window[0].format(EVDS_DATE_FMT);
                String end = window[1].format(EVDS_DATE_FMT);

                EvdsBondDataResponse response = evdsClient.fetchBondData(List.of(oranCode), start, end);
                List<BondRateItemDto> rateItems = clientMapper.toRateItemDtos(response, oranCode);

                for (BondRateItemDto rateItem : rateItems) {
                    if (!rateHistoryRepository.existsByIsinCodeAndRateDate(isinCode, rateItem.rateDate())) {
                        allRecords.add(BondRateHistory.builder()
                                .bond(bond)
                                .rateDate(rateItem.rateDate())
                                .couponRate(rateItem.couponRate())
                                .build());
                    }
                }
                successWindows++;
            } catch (CallNotPermittedException e) {
                log.error("EVDS circuit breaker OPEN during rate fetch for {} at window {}/{}", isinCode, i + 1,
                        windows.size());
                throw e;
            } catch (Exception e) {
                failedWindows++;
                log.error("Rate history window {}/{} failed for {}: {}", i + 1, windows.size(), isinCode,
                        e.getMessage());
            }
        }

        if (successWindows == 0 && failedWindows > 0) {
            throw new BusinessException(
                    "All " + failedWindows + " rate history windows failed for " + isinCode);
        }

        log.debug("Rate history for {}: {} windows OK, {} failed, {} records prepared",
                isinCode, successWindows, failedWindows, allRecords.size());
        return allRecords;
    }

    private List<BondSnapshotDto> fetchSnapshotData(List<BondSerieDto> bondSeries) {
        List<List<BondSerieDto>> batches = BondSerieFilterUtil.partition(bondSeries, batchSize);
        String today = LocalDate.now().format(EVDS_DATE_FMT);

        log.info("Fetching snapshot data: {} bonds in {} batches (batchSize={}) for date={}",
                bondSeries.size(), batches.size(), batchSize, today);

        List<BondSnapshotDto> allSnapshots = new ArrayList<>();
        int successBatch = 0;
        int failBatch = 0;
        List<String> failedBatches = new ArrayList<>();

        for (int i = 0; i < batches.size(); i++) {
            List<BondSerieDto> batch = batches.get(i);
            List<String> codes = new ArrayList<>();
            for (BondSerieDto serie : batch) {
                codes.add(serie.serieCode());
                codes.add(BondSerieFilterUtil.toOranCode(serie.isin()));
            }
            try {
                log.debug("Snapshot batch {}/{}: {} bonds, {} codes", i + 1, batches.size(), batch.size(),
                        codes.size());
                EvdsBondDataResponse response = evdsClient.fetchBondData(codes, today, today);
                List<BondSnapshotDto> batchSnapshots = clientMapper.toSnapshotDtos(batch, response);
                allSnapshots.addAll(batchSnapshots);
                successBatch++;
                log.debug("Snapshot batch {}/{} returned {} snapshots", i + 1, batches.size(), batchSnapshots.size());
            } catch (CallNotPermittedException e) {
                log.warn("EVDS circuit breaker OPEN, stopping snapshot fetch at batch {}/{}", i + 1, batches.size());
                break;
            } catch (Exception e) {
                failBatch++;
                failedBatches.add("batch-" + (i + 1));
                log.error("Snapshot batch {}/{} failed: {}", i + 1, batches.size(), e.getMessage(), e);
                BatchFailureGuard.check(successBatch, failBatch, failedBatches, "bond snapshot");
            }
        }
        log.info("Snapshot fetch complete: {}/{} batches OK, {} DTOs total", successBatch, batches.size(),
                allSnapshots.size());
        return allSnapshots;
    }

    private List<BondSerieDto> fetchAndFilterSeries() {
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
}
