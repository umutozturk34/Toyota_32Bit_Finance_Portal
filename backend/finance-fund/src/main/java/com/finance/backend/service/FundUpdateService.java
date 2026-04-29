package com.finance.backend.service;

import com.finance.backend.client.TefasClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.config.FundProperties;
import com.finance.backend.config.WindowingPolicy;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.mapper.FundMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.FundType;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundCandleRepository;
import com.finance.backend.repository.FundRepository;
import com.finance.backend.util.CodeNormalizer;
import com.finance.backend.util.CandleBatchUpsertTemplate;
import com.finance.backend.util.CandlePruner;
import com.finance.backend.util.TefasHelper;
import com.finance.backend.util.WindowedFetchPlanner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
public class FundUpdateService implements CandleBatchRefresher {

    private final TefasClient tefasClient;
    private final FundMapper fundMapper;
    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final FundSnapshotProcessor snapshotProcessor;
    private final FundBulkFetchExecutor bulkFetchExecutor;
    private final TransactionTemplate transactionTemplate;
    private final WindowingPolicy windowing;
    private final ZoneId appZone;

    public FundUpdateService(TefasClient tefasClient,
                             FundMapper fundMapper,
                             FundRepository fundRepository,
                             FundCandleRepository fundCandleRepository,
                             MarketCacheService<Fund, FundCandle> fundCacheService,
                             TrackedAssetQueryService trackedAssetQueryService,
                             FundSnapshotProcessor snapshotProcessor,
                             FundBulkFetchExecutor bulkFetchExecutor,
                             TransactionTemplate transactionTemplate,
                             AppProperties appProperties,
                             FundProperties fundProperties) {
        this.tefasClient = tefasClient;
        this.fundMapper = fundMapper;
        this.fundRepository = fundRepository;
        this.fundCandleRepository = fundCandleRepository;
        this.fundCacheService = fundCacheService;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.snapshotProcessor = snapshotProcessor;
        this.bulkFetchExecutor = bulkFetchExecutor;
        this.transactionTemplate = transactionTemplate;
        this.windowing = WindowingPolicy.from(fundProperties);
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.FUND;
    }

    public void refreshAll() {
        long totalStart = System.currentTimeMillis();
        snapshotProcessor.refreshAll();
        refreshAllCandles();
        log.info("[TIMING] Total fund update took {}s", (System.currentTimeMillis() - totalStart) / 1000);
    }

    public void refresh(String fundCode) {
        snapshotProcessor.refreshOne(fundCode);
        refreshCandles(fundCode);
    }

    public boolean exists(String fundCode) {
        return snapshotProcessor.exists(fundCode);
    }

    private void refreshAllCandles() {
        log.info("Starting fund candle update (bulk strategy)");
        CandlePruner.pruneByYears(transactionTemplate, windowing.yearsToFetch(),
                cutoffDate -> fundCandleRepository.deleteByCandleDateBefore(cutoffDate));
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
        LocalDate earliest = today.minusYears(windowing.yearsToFetch());

        List<WindowedFetchPlanner.DateWindow> windows = computeRequiredWindows(
                trackedFunds, earliest, today);
        if (windows.isEmpty()) {
            log.info("All tracked {} funds up to date, skipping fetch", fundType);
            return;
        }

        bulkFetchExecutor.runWindows(fundType, windows, trackedByCode,
                (fund, dtos) -> {
                    int saved = saveCandleBatch(fund, fundType, dtos);
                    fundCacheService.refreshHistory(fund.getFundCode());
                    return saved;
                });
    }

    private List<Fund> collectTrackedFunds(FundType fundType) {
        return switch (fundType) {
            case BYF -> fundRepository.findByFundType(FundType.BYF);
            case YAT -> fundRepository.findAllById(
                            trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND))
                    .stream()
                    .filter(f -> f.getFundType() != FundType.BYF)
                    .toList();
        };
    }

    private static final int BACKFILL_GAP_THRESHOLD_DAYS = 30;

    private List<WindowedFetchPlanner.DateWindow> computeRequiredWindows(
            List<Fund> funds, LocalDate earliest, LocalDate today) {
        Map<String, CandleDateRange> rangePerFund = fundCandleRepository.findCandleDateRangePerFund().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> new CandleDateRange(
                                ((LocalDateTime) row[1]).toLocalDate(),
                                ((LocalDateTime) row[2]).toLocalDate())));
        LocalDate minFetchFrom = null;
        for (Fund fund : funds) {
            LocalDate fetchFrom = computeFetchFrom(rangePerFund.get(fund.getFundCode()), earliest, today);
            if (fetchFrom == null) continue;
            if (minFetchFrom == null || fetchFrom.isBefore(minFetchFrom)) {
                minFetchFrom = fetchFrom;
            }
        }
        if (minFetchFrom == null) return List.of();
        return WindowedFetchPlanner.planBackward(minFetchFrom, today, windowing.windowSizeDays());
    }

    private LocalDate computeFetchFrom(CandleDateRange range, LocalDate earliest, LocalDate today) {
        if (range == null) return earliest;
        if (range.min().isAfter(earliest.plusDays(BACKFILL_GAP_THRESHOLD_DAYS))) return earliest;
        if (range.max().isBefore(today)) return range.max().plusDays(1);
        return null;
    }

    private record CandleDateRange(LocalDate min, LocalDate max) {}

    @Override
    public void refreshCandles(String fundCode) {
        String normalized = CodeNormalizer.upper(fundCode);
        if (normalized.isBlank()) {
            return;
        }

        Fund fund = fundRepository.findById(normalized).orElse(null);
        if (fund == null) {
            snapshotProcessor.refreshOne(normalized);
            fund = fundRepository.findById(normalized).orElse(null);
        }
        if (fund == null) {
            log.warn("Tracked fund {} is not available for candle refresh", normalized);
            return;
        }

        Fund targetFund = fund;
        FundType fundType = targetFund.getFundType() == null ? FundType.YAT : targetFund.getFundType();
        long existingCount = fundCandleRepository.countByFundCode(targetFund.getFundCode());
        if (existingCount >= windowing.minCandlesForIncremental()) {
            transactionTemplate.execute(status -> fetchAndSaveSinceLastCandle(targetFund, fundType));
        } else {
            fetchAndSaveFullHistory(targetFund, fundType);
        }
        fundCacheService.refreshHistory(targetFund.getFundCode());
        log.info("Refreshed tracked fund candles for {}", normalized);
    }

    private int fetchAndSaveSinceLastCandle(Fund fund, FundType fundType) {
        LocalDate today = TefasHelper.findLastBusinessDay(
                LocalDate.now(appZone), appZone, windowing.eodCutoverHour());
        LocalDate fromDate = fundCandleRepository.findFirstByFundCodeOrderByCandleDateDesc(fund.getFundCode())
                .map(candle -> candle.getCandleDate().toLocalDate())
                .orElse(today);
        return fromDate.isBefore(today)
                ? runWindowedSingleFund(fund, fundType, fromDate, today)
                : 0;
    }

    private int fetchAndSaveFullHistory(Fund fund, FundType fundType) {
        LocalDate today = TefasHelper.findLastBusinessDay(
                LocalDate.now(appZone), appZone, windowing.eodCutoverHour());
        LocalDate earliest = today.minusYears(windowing.yearsToFetch());
        long start = System.currentTimeMillis();
        int total = runWindowedSingleFund(fund, fundType, earliest, today);
        log.info("[TIMING] {} full history done - {} candles in {}s",
                fund.getFundCode(), total, (System.currentTimeMillis() - start) / 1000);
        return total;
    }

    private int runWindowedSingleFund(Fund fund, FundType fundType, LocalDate from, LocalDate to) {
        List<WindowedFetchPlanner.DateWindow> windows = WindowedFetchPlanner
                .planBackward(from, to, windowing.windowSizeDays());
        int totalSaved = 0;
        for (WindowedFetchPlanner.DateWindow window : windows) {
            int saved = tryFetchWindow(fund, fundType, window.start(), window.end());
            if (saved > 0) totalSaved += saved;
        }
        return totalSaved;
    }

    private int tryFetchWindow(Fund fund, FundType fundType, LocalDate start, LocalDate end) {
        List<TefasFundDto> candles = tefasClient.post(fundType, fund.getFundCode(), start, end);
        if (candles.isEmpty()) return 0;
        return transactionTemplate.execute(status -> saveCandleBatch(fund, fundType, candles));
    }

    private int saveCandleBatch(Fund fund, FundType fundType, List<TefasFundDto> dtos) {
        CandleBatchUpsertTemplate.UpsertResult<FundCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                dtos,
                TefasFundDto::date,
                keys -> fundCandleRepository.findByFundCodeAndCandleDateIn(fund.getFundCode(), keys),
                FundCandle::getCandleDate,
                fundMapper::updateCandleEntity,
                dto -> fundMapper.toCandleEntity(dto, fund, fundType));

        if (!upsertResult.newEntities().isEmpty()) {
            fundCandleRepository.saveAll(upsertResult.newEntities());
        }
        return upsertResult.totalChanged();
    }
}
