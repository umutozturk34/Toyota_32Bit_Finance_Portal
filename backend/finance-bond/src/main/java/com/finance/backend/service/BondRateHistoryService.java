package com.finance.backend.service;

import com.finance.backend.client.EvdsClient;
import com.finance.backend.config.BondProperties;
import com.finance.backend.dto.external.BondRateItemDto;
import com.finance.backend.dto.external.BondSnapshotDto;
import com.finance.backend.dto.internal.EvdsBondDataResponse;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.BondMapper;
import com.finance.backend.mapper.EvdsBondClientMapper;
import com.finance.backend.model.Bond;
import com.finance.backend.model.BondRateHistory;
import com.finance.backend.repository.BondRateHistoryRepository;
import com.finance.backend.repository.BondRepository;
import com.finance.backend.util.BondSerieFilterUtil;
import com.finance.backend.util.WindowedFetchPlanner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Log4j2
public class BondRateHistoryService {

    private static final DateTimeFormatter EVDS_DATE_FMT = EvdsClient.DATE_FMT;

    private final EvdsClient evdsClient;
    private final EvdsBondClientMapper clientMapper;
    private final BondMapper bondMapper;
    private final BondRepository bondRepository;
    private final BondRateHistoryRepository rateHistoryRepository;
    private final MarketCacheService<Bond> bondCacheService;
    private final TransactionTemplate transactionTemplate;
    private final int maxDaysPerRequest;
    private final BigDecimal auctionThreshold;
    private final BigDecimal cpiFixedThreshold;
    private final BigDecimal faceValue;
    private final int daysInYear;

    public BondRateHistoryService(EvdsClient evdsClient,
                                  EvdsBondClientMapper clientMapper,
                                  BondMapper bondMapper,
                                  BondRepository bondRepository,
                                  BondRateHistoryRepository rateHistoryRepository,
                                  MarketCacheService<Bond> bondCacheService,
                                  TransactionTemplate transactionTemplate,
                                  BondProperties bondProperties) {
        this.evdsClient = evdsClient;
        this.clientMapper = clientMapper;
        this.bondMapper = bondMapper;
        this.bondRepository = bondRepository;
        this.rateHistoryRepository = rateHistoryRepository;
        this.bondCacheService = bondCacheService;
        this.transactionTemplate = transactionTemplate;
        this.maxDaysPerRequest = bondProperties.getMaxDaysPerRequest();
        this.auctionThreshold = bondProperties.getAuctionThreshold();
        this.cpiFixedThreshold = bondProperties.getCpiFixedThreshold();
        this.faceValue = bondProperties.getFaceValue();
        this.daysInYear = bondProperties.getDaysInYear();
    }

    public void processSingleBond(BondSnapshotDto dto, LocalDateTime now) {
        Optional<Bond> existingOpt = bondRepository.findById(dto.seriesCode());
        boolean isNew = existingOpt.isEmpty();

        Bond bond;
        if (existingOpt.isPresent()) {
            bond = existingOpt.get();
            bondMapper.updateEntity(bond, dto, now);
            bond.resolveNextCouponDate();
        } else {
            bond = bondMapper.toEntity(dto, now);
            bond.setIssuer("HAZİNE");
            bond.resolveNextCouponDate();
        }

        BondSerieFilterUtil.sanitizeCouponRate(bond, dto);

        Bond savedBond = transactionTemplate.execute(status -> bondRepository.save(bond));

        boolean zeroCoupon = savedBond.getCouponRate() == null
                || savedBond.getCouponRate().compareTo(BigDecimal.ZERO) == 0;

        List<BondRateHistory> newRateRecords;
        if (zeroCoupon) {
            newRateRecords = List.of();
            log.debug("{} - zero coupon, skipping rate history", dto.isinCode());
        } else {
            newRateRecords = buildRateRecords(dto, savedBond);
        }

        if (!newRateRecords.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> rateHistoryRepository.saveAll(newRateRecords));
        }

        List<BondRateHistory> fullHistory = rateHistoryRepository.findByIsinCodeOrderByRateDateAsc(dto.isinCode());
        savedBond.resolveType(fullHistory, auctionThreshold, cpiFixedThreshold);
        savedBond.resolveSimpleYield(faceValue, daysInYear);

        if (savedBond.isDiscounted()) {
            savedBond.setNextCouponDate(null);
        }

        transactionTemplate.executeWithoutResult(status -> bondRepository.save(savedBond));
        bondCacheService.putSnapshot(savedBond.getSeriesCode(), savedBond);

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
            log.debug("{} - no history, full fetch from {}", isinCode, startDate);

            List<BondRateHistory> fetched = fetchRateHistory(isinCode, bond, startDate, today);
            addTodayFromSnapshotIfMissing(fetched, dto, bond, today);
            return fetched;
        }

        LocalDate lastDate = latestOpt.get().getRateDate();
        long gapDays = ChronoUnit.DAYS.between(lastDate, today);

        if (gapDays <= 1) {
            log.debug("{} - snapshot only: lastDate={}, gapDays={}", isinCode, lastDate, gapDays);
            List<BondRateHistory> result = new ArrayList<>();
            addTodayFromSnapshotIfMissing(result, dto, bond, today);
            return result;
        }

        LocalDate fetchStart = lastDate.plusDays(1);
        log.debug("{} - incremental fetch: lastDate={}, gapDays={}, fetchStart={}",
                isinCode, lastDate, gapDays, fetchStart);

        List<BondRateHistory> fetched = fetchRateHistory(isinCode, bond, fetchStart, today);
        addTodayFromSnapshotIfMissing(fetched, dto, bond, today);
        return fetched;
    }

    private void addTodayFromSnapshotIfMissing(List<BondRateHistory> records, BondSnapshotDto dto, Bond bond,
                                                LocalDate today) {
        if (dto.couponRate() == null) {
            return;
        }

        boolean todayExists = records.stream().anyMatch(r -> today.equals(r.getRateDate()));
        if (todayExists) {
            return;
        }

        if (rateHistoryRepository.existsByIsinCodeAndRateDate(dto.isinCode(), today)) {
            return;
        }

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

        List<WindowedFetchPlanner.DateWindow> windows = WindowedFetchPlanner
                .planForward(startDate, endDate, maxDaysPerRequest);
        log.debug("Fetching rate history for {}: {} to {} ({} windows)", isinCode, startDate, endDate, windows.size());

        List<BondRateHistory> allRecords = new ArrayList<>();
        int successWindows = 0;
        int failedWindows = 0;

        for (int i = 0; i < windows.size(); i++) {
            WindowedFetchPlanner.DateWindow window = windows.get(i);
            try {
                String start = window.start().format(EVDS_DATE_FMT);
                String end = window.end().format(EVDS_DATE_FMT);

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
}
