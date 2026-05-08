package com.finance.market.fund.service;
import com.finance.cache.service.MarketCacheService;

import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.market.fund.client.TefasClient;
import com.finance.common.config.AppProperties;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.config.WindowingPolicy;
import com.finance.market.fund.dto.external.TefasFundDto;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundType;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.market.fund.repository.FundRepository;
import com.finance.common.util.CodeNormalizer;
import com.finance.market.fund.util.TefasHelper;
import com.finance.common.util.WindowedFetchPlanner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Log4j2
@Service
public class FundCandleIncrementalRefreshService {

    private final TefasClient tefasClient;
    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final MarketCacheService<Fund> fundCacheService;
    private final FundSnapshotProcessor snapshotProcessor;
    private final FundEntityWriter entityWriter;
    private final TransactionTemplate transactionTemplate;
    private final WindowingPolicy windowing;
    private final ZoneId appZone;

    public FundCandleIncrementalRefreshService(TefasClient tefasClient,
                                               FundRepository fundRepository,
                                               FundCandleRepository fundCandleRepository,
                                               MarketCacheService<Fund> fundCacheService,
                                               FundSnapshotProcessor snapshotProcessor,
                                               FundEntityWriter entityWriter,
                                               TransactionTemplate transactionTemplate,
                                               AppProperties appProperties,
                                               FundProperties fundProperties) {
        this.tefasClient = tefasClient;
        this.fundRepository = fundRepository;
        this.fundCandleRepository = fundCandleRepository;
        this.fundCacheService = fundCacheService;
        this.snapshotProcessor = snapshotProcessor;
        this.entityWriter = entityWriter;
        this.transactionTemplate = transactionTemplate;
        this.windowing = WindowingPolicy.from(fundProperties);
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    public void refresh(String fundCode) {
        String normalized = CodeNormalizer.upper(fundCode);
        if (normalized.isBlank()) return;

        Fund fund = resolveFund(normalized);
        if (fund == null) {
            log.warn("Tracked fund {} is not available for candle refresh", normalized);
            return;
        }

        FundType fundType = fund.getFundType() == null ? FundType.YAT : fund.getFundType();
        long existingCount = fundCandleRepository.countByFundCode(fund.getFundCode());
        if (existingCount >= windowing.minCandlesForIncremental()) {
            fetchAndSaveSinceLastCandle(fund, fundType);
        } else {
            fetchAndSaveFullHistory(fund, fundType);
        }
        refreshChangePercentForLatest(fund);
        log.info("Refreshed tracked fund candles for {}", normalized);
    }

    private Fund resolveFund(String normalized) {
        Fund fund = fundRepository.findById(normalized).orElse(null);
        if (fund == null) {
            snapshotProcessor.refreshOne(normalized);
            fund = fundRepository.findById(normalized).orElse(null);
        }
        return fund;
    }

    private void refreshChangePercentForLatest(Fund fund) {
        fundCandleRepository.findFirstByFundCodeOrderByCandleDateDesc(fund.getFundCode())
                .ifPresent(latest -> {
                    if (entityWriter.refreshChangePercent(fund, latest.getCandleDate())) {
                        fundCacheService.putSnapshot(fund.getFundCode(), fund);
                    }
                });
    }

    private int fetchAndSaveSinceLastCandle(Fund fund, FundType fundType) {
        LocalDate today = TefasHelper.findLastBusinessDay(
                LocalDate.now(appZone), appZone, windowing.eodCutoverHour());
        LocalDate fromDate = fundCandleRepository.findFirstByFundCodeOrderByCandleDateDesc(fund.getFundCode())
                .map(candle -> candle.getCandleDate().toLocalDate())
                .orElse(today);
        return fromDate.isBefore(today) ? runWindowedSingleFund(fund, fundType, fromDate, today) : 0;
    }

    private int fetchAndSaveFullHistory(Fund fund, FundType fundType) {
        LocalDate today = TefasHelper.findLastBusinessDay(
                LocalDate.now(appZone), appZone, windowing.eodCutoverHour());
        LocalDate earliest = today.minusYears(windowing.yearsToFetch()).plusDays(1);
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
        return transactionTemplate.execute(status -> entityWriter.saveCandleBatch(fund, fundType, candles));
    }
}
