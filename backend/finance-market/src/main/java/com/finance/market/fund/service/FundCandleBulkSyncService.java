package com.finance.market.fund.service;
import com.finance.market.core.service.TrackedAssetQueryService;



import com.finance.common.config.AppProperties;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.config.WindowingPolicy;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.market.fund.repository.FundRepository;
import com.finance.market.fund.util.TefasHelper;
import com.finance.market.core.util.WindowedFetchPlanner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
public class FundCandleBulkSyncService {

    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final FundEntityWriter entityWriter;
    private final FundBulkFetchExecutor bulkFetchExecutor;
    private final WindowingPolicy windowing;
    private final ZoneId appZone;

    public FundCandleBulkSyncService(FundRepository fundRepository,
                                     FundCandleRepository fundCandleRepository,
                                     TrackedAssetQueryService trackedAssetQueryService,
                                     FundEntityWriter entityWriter,
                                     FundBulkFetchExecutor bulkFetchExecutor,
                                     AppProperties appProperties,
                                     FundProperties fundProperties) {
        this.fundRepository = fundRepository;
        this.fundCandleRepository = fundCandleRepository;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.entityWriter = entityWriter;
        this.bulkFetchExecutor = bulkFetchExecutor;
        this.windowing = WindowingPolicy.from(fundProperties);
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    public void refreshAllCandles() {
        log.info("Starting fund candle update (bulk strategy)");
        long byfStart = System.currentTimeMillis();
        bulkUpdateForType(FundType.BYF);
        log.info("[TIMING] BYF bulk candle update took {}s", (System.currentTimeMillis() - byfStart) / 1000);
        long yatStart = System.currentTimeMillis();
        bulkUpdateForType(FundType.YAT);
        log.info("[TIMING] YAT bulk candle update took {}s", (System.currentTimeMillis() - yatStart) / 1000);
    }

    private void bulkUpdateForType(FundType fundType) {
        List<Fund> trackedFunds = collectTrackedFunds(fundType);
        if (trackedFunds.isEmpty()) {
            log.warn("No tracked {} funds found", fundType);
            return;
        }
        Map<String, Fund> trackedByCode = trackedFunds.stream()
                .collect(Collectors.toMap(Fund::getFundCode, f -> f));
        LocalDate today = TefasHelper.findLastBusinessDay(
                LocalDate.now(appZone), appZone, windowing.eodCutoverHour());
        LocalDate earliest = today.minusYears(windowing.yearsToFetch()).plusDays(1);

        List<WindowedFetchPlanner.DateWindow> windows = computeRequiredWindows(trackedFunds, earliest, today);
        if (windows.isEmpty()) {
            log.info("All tracked {} funds up to date, skipping fetch", fundType);
            return;
        }

        bulkFetchExecutor.runWindows(fundType, windows, trackedByCode,
                (fund, dtos) -> entityWriter.saveCandleBatch(fund, fundType, dtos));
    }

    private List<Fund> collectTrackedFunds(FundType fundType) {
        List<Fund> tracked = fundRepository.findAllById(
                trackedAssetQueryService.getCodes(TrackedAssetType.FUND));
        return switch (fundType) {
            case BYF -> tracked.stream().filter(f -> f.getFundType() == FundType.BYF).toList();
            case YAT -> tracked.stream().filter(f -> f.getFundType() != FundType.BYF).toList();
        };
    }

    private static final int GAP_DETECTION_LOOKBACK_DAYS = 30;

    private List<WindowedFetchPlanner.DateWindow> computeRequiredWindows(
            List<Fund> funds, LocalDate earliest, LocalDate today) {
        Map<String, Long> countPerFund = fundCandleRepository.countCandlesPerFund().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
        Map<String, LocalDate> maxPerFund = fundCandleRepository.findCandleDateRangePerFund().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((LocalDateTime) row[2]).toLocalDate()));

        boolean anyNeedsFullFetch = false;
        LocalDate forwardStart = null;
        int minCandles = windowing.minCandlesForIncremental();
        for (Fund fund : funds) {
            long count = countPerFund.getOrDefault(fund.getFundCode(), 0L);
            if (count < minCandles) {
                anyNeedsFullFetch = true;
                continue;
            }
            LocalDate max = maxPerFund.get(fund.getFundCode());
            if (max != null && max.isBefore(today)) {
                LocalDate fundForwardStart = max.plusDays(1);
                if (forwardStart == null || fundForwardStart.isBefore(forwardStart)) forwardStart = fundForwardStart;
            }
            LocalDate gapStart = detectGapStart(fund.getFundCode(), today);
            if (gapStart != null && (forwardStart == null || gapStart.isBefore(forwardStart))) {
                forwardStart = gapStart;
            }
        }

        if (anyNeedsFullFetch) {
            return WindowedFetchPlanner.planBackward(earliest, today, windowing.windowSizeDays());
        }
        if (forwardStart != null) {
            return WindowedFetchPlanner.planForward(forwardStart, today, windowing.windowSizeDays());
        }
        return List.of();
    }

    private LocalDate detectGapStart(String fundCode, LocalDate today) {
        LocalDate windowStart = today.minusDays(GAP_DETECTION_LOOKBACK_DAYS);
        java.util.Set<LocalDate> stored = fundCandleRepository
                .findCandleDatesSince(fundCode, windowStart.atStartOfDay()).stream()
                .map(LocalDateTime::toLocalDate)
                .collect(Collectors.toSet());
        for (LocalDate d = windowStart; d.isBefore(today); d = d.plusDays(1)) {
            java.time.DayOfWeek dow = d.getDayOfWeek();
            if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) continue;
            if (!stored.contains(d)) return d;
        }
        return null;
    }
}
