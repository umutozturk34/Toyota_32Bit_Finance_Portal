package com.finance.backend.service;

import com.finance.backend.client.TefasClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.mapper.FundMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.FundType;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundCandleRepository;
import com.finance.backend.repository.FundRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CandleBatchUpsertTemplate;
import com.finance.backend.util.CandlePruner;
import com.finance.backend.util.TefasHelper;
import com.finance.backend.util.WindowedFetchPlanner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Log4j2
@Service
public class FundCandleService implements CandleBatchRefresher {

    private final TefasClient tefasClient;
    private final FundMapper fundMapper;
    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final FundSnapshotService fundSnapshotService;
    private final TransactionTemplate transactionTemplate;
    private final int windowSize;
    private final int minCandlesForIncremental;
    private final int yearsToFetch;
    private final ZoneId appZone;

    public FundCandleService(TefasClient tefasClient,
                             FundMapper fundMapper,
                             FundRepository fundRepository,
                             FundCandleRepository fundCandleRepository,
                             MarketCacheService<Fund, FundCandle> fundCacheService,
                             TrackedAssetQueryService trackedAssetQueryService,
                             FundSnapshotService fundSnapshotService,
                             PlatformTransactionManager transactionManager,
                             AppProperties appProperties) {
        this.tefasClient = tefasClient;
        this.fundMapper = fundMapper;
        this.fundRepository = fundRepository;
        this.fundCandleRepository = fundCandleRepository;
        this.fundCacheService = fundCacheService;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.fundSnapshotService = fundSnapshotService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        AppProperties.Fund fundConfig = appProperties.getFund();
        this.windowSize = fundConfig.getWindowSizes();
        this.minCandlesForIncremental = fundConfig.getMinCandlesForIncremental();
        this.yearsToFetch = fundConfig.getYearsToFetch();
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.FUND;
    }

    @Override
    public void refreshAll() {
        long totalStart = System.currentTimeMillis();
        log.info("Starting fund candle update");
        pruneOldCandles();
        long byfStart = System.currentTimeMillis();
        updateCandlesForType(FundType.BYF);
        log.info("[TIMING] BYF candle update took {}s", (System.currentTimeMillis() - byfStart) / 1000);
        long yatStart = System.currentTimeMillis();
        updateCandlesForType(FundType.YAT);
        log.info("[TIMING] YAT candle update took {}s", (System.currentTimeMillis() - yatStart) / 1000);
        log.info("[TIMING] Total fund candle update took {}s", (System.currentTimeMillis() - totalStart) / 1000);
    }

    public void refreshTrackedFundCandles(String fundCode) {
        String normalized = fundCode == null ? "" : fundCode.trim().toUpperCase();
        if (normalized.isBlank()) {
            return;
        }

        Fund fund = fundRepository.findById(normalized).orElse(null);
        if (fund == null) {
            fundSnapshotService.refreshTrackedFundSnapshot(normalized);
            fund = fundRepository.findById(normalized).orElse(null);
        }

        if (fund == null) {
            log.warn("Tracked fund {} is not available for candle refresh", normalized);
            return;
        }

        Fund targetFund = fund;
        FundType fundType = targetFund.getFundType() == null ? FundType.YAT : targetFund.getFundType();
        long existingCount = fundCandleRepository.countByFundCode(targetFund.getFundCode());
        if (existingCount >= minCandlesForIncremental) {
            transactionTemplate.execute(status -> fetchAndSaveSinceLastCandle(targetFund, fundType));
        } else {
            fetchAndSaveFullHistory(targetFund, fundType);
        }

        fundCacheService.refreshHistory(targetFund.getFundCode());
        log.info("Refreshed tracked fund candles for {}", normalized);
    }

    private void pruneOldCandles() {
        CandlePruner.pruneByYears(
                transactionTemplate,
                yearsToFetch,
                cutoffDate -> fundCandleRepository.deleteByCandleDateBefore(cutoffDate));
    }

    private void updateCandlesForType(FundType fundType) {
        List<Fund> funds;
        if (fundType == FundType.BYF) {
            funds = fundRepository.findByFundType(FundType.BYF);
        } else {
            List<String> yatCodes = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND);
            funds = fundRepository.findAllById(yatCodes).stream()
                    .filter(f -> f.getFundType() != FundType.BYF)
                    .toList();
        }

        if (funds.isEmpty()) {
            log.warn("No {} funds found", fundType);
            return;
        }

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                funds,
                fund -> {
                    long existingCount = fundCandleRepository.countByFundCode(fund.getFundCode());

                    if (existingCount >= minCandlesForIncremental) {
                        transactionTemplate.execute(status -> fetchAndSaveSinceLastCandle(fund, fundType));
                    } else {
                        fetchAndSaveFullHistory(fund, fundType);
                    }

                    fundCacheService.refreshHistory(fund.getFundCode());
                },
                Fund::getFundCode,
                fundType + " candle",
                5,
                (fund, e) -> log.error("Failed candle update for {} ({}): {}", fund.getFundCode(), fundType, e.getMessage(), e),
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("TEFAS circuit breaker is OPEN, stopping {} candle update after {} success, {} failed",
                        fundType, stopped.successCount(), stopped.failCount()));

        BatchLogHelper.logSummary(log, fundType + " candle update", result);
    }

    private int fetchAndSaveSinceLastCandle(Fund fund, FundType fundType) {
        LocalDate today = findLastBusinessDay(LocalDate.now());
        LocalDate fromDate = fundCandleRepository.findFirstByFundCodeOrderByCandleDateDesc(fund.getFundCode())
                .map(candle -> candle.getCandleDate().toLocalDate())
                .orElse(today);
        List<TefasFundDto> candles = fetchTefas(fundType, fund.getFundCode(), fromDate, today);
        if (candles.isEmpty()) {
            return 0;
        }
        return saveCandleBatch(fund, fundType, candles);
    }

    private int fetchAndSaveFullHistory(Fund fund, FundType fundType) {
        long histStart = System.currentTimeMillis();
        LocalDate finalEndDate = findLastBusinessDay(LocalDate.now(appZone));
        LocalDate limitDate = finalEndDate.minusYears(yearsToFetch);
        int totalSaved = 0;
        List<WindowedFetchPlanner.DateWindow> windows = WindowedFetchPlanner
                .planBackward(limitDate, finalEndDate, windowSize);

        log.info("[TIMING] {} full history start - {} windows planned", fund.getFundCode(), windows.size());

        for (WindowedFetchPlanner.DateWindow window : windows) {
            int saved = tryFetchWindow(fund, fundType, window.start(), window.end());

            if (saved > 0) {
                totalSaved += saved;
            }

            if (totalSaved > 0 && saved <= 0 && window.start().isBefore(finalEndDate.minusYears(1))) {
                log.info("[TIMING] {} early exit at window {} (no data past 1yr)", fund.getFundCode(), window.start());
                break;
            }
        }

        long histMs = System.currentTimeMillis() - histStart;
        log.info("[TIMING] {} full history done - {} candles in {}s ({} windows)", fund.getFundCode(), totalSaved, histMs / 1000, windows.size());
        return totalSaved;
    }

    private int tryFetchWindow(Fund fund, FundType fundType, LocalDate start, LocalDate end) {
        try {
            long windowStart = System.currentTimeMillis();
            List<TefasFundDto> candles = fetchTefas(fundType, fund.getFundCode(), start, end);
            long fetchMs = System.currentTimeMillis() - windowStart;

            if (candles.isEmpty()) {
                log.info("[TIMING] {} window {} to {} - fetch={}ms, empty response", fund.getFundCode(), start, end, fetchMs);
                return 0;
            }

            int saved = transactionTemplate.execute(status -> saveCandleBatch(fund, fundType, candles));
            long totalMs = System.currentTimeMillis() - windowStart;
            log.info("[TIMING] {} window {} to {} - fetch={}ms, save={}ms, total={}ms, {} candles",
                    fund.getFundCode(), start, end, fetchMs, totalMs - fetchMs, totalMs, saved);
            return saved;
        } catch (CallNotPermittedException e) {
            log.warn("{} - TEFAS circuit breaker is OPEN, skipping", fund.getFundCode());
            throw e;
        } catch (Exception e) {
            log.warn("{} - Window {} to {} failed: {}", fund.getFundCode(), start, end, e.getMessage(), e);
            return -1;
        }
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

        log.debug("{} - Batch: {} new, {} updated",
                fund.getFundCode(), upsertResult.insertCount(), upsertResult.updateCount());
        return upsertResult.totalChanged();
    }

    private List<TefasFundDto> fetchTefas(FundType fundType, String fundCode,
                                          LocalDate startDate, LocalDate endDate) {
        return TefasHelper.fetchTefas(tefasClient, fundType, fundCode, startDate, endDate);
    }

    private LocalDate findLastBusinessDay(LocalDate from) {
        return TefasHelper.findLastBusinessDay(from, appZone);
    }
}
