package com.finance.backend.service;

import com.finance.backend.client.TefasClient;
import com.finance.backend.constants.MarketConstants;
import com.finance.backend.dto.external.TefasResponse;
import com.finance.backend.mapper.FundMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.repository.FundCandleRepository;
import com.finance.backend.repository.FundRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FundDataService {

    private static final int[] WINDOW_SIZES = {65, 60, 30, 15};
    private static final int SKIP_DAYS = 15;
    private static final int MIN_CANDLES_FOR_INCREMENTAL = 1000;
    private static final int YEARS_TO_FETCH = 5;

    private final TefasClient tefasClient;
    private final FundMapper fundMapper;
    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final MarketConstants marketConstants;
    private final FundDataService self;

    public FundDataService(TefasClient tefasClient,
                           FundMapper fundMapper,
                           FundRepository fundRepository,
                           FundCandleRepository fundCandleRepository,
                           MarketCacheService<Fund, FundCandle> fundCacheService,
                           MarketConstants marketConstants,
                           @Lazy FundDataService self) {
        this.tefasClient = tefasClient;
        this.fundMapper = fundMapper;
        this.fundRepository = fundRepository;
        this.fundCandleRepository = fundCandleRepository;
        this.fundCacheService = fundCacheService;
        this.marketConstants = marketConstants;
        this.self = self;
    }

    private LocalDate findLastBusinessDay(LocalDate from) {
        var istanbulNow = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Istanbul"));
        LocalDate date = from;
        if (date.equals(istanbulNow.toLocalDate()) && istanbulNow.getHour() < 11) {
            date = date.minusDays(1);
        }
        for (int i = 0; i < 5; i++) {
            var dow = date.getDayOfWeek();
            if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                return date;
            }
            date = date.minusDays(1);
        }
        return date;
    }

    public void updateFundSnapshots() {
        LocalDate today = findLastBusinessDay(LocalDate.now());
        log.info("Starting fund snapshot update (date={})...", today);

        int successCount = 0;
        int failCount = 0;

        try {
            TefasResponse byfResponse = tefasClient.fetchAllByfFunds(today);
            if (byfResponse != null && byfResponse.data() != null) {
                for (TefasResponse.FundData dto : byfResponse.data()) {
                    try {
                        Fund saved = self.saveFundSnapshot(dto, "BYF");
                        fundCacheService.putSnapshot(saved.getFundCode(), saved);
                        successCount++;
                    } catch (Exception e) {
                        failCount++;
                        log.error("Failed to save BYF snapshot {}: {}", dto.fonKodu(), e.getMessage());
                    }
                }
            }
            log.info("BYF snapshots done: {} success, {} failed", successCount, failCount);
        } catch (Exception e) {
            log.error("Failed to fetch BYF funds: {}", e.getMessage());
        }

        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        List<String> yatCodes = marketConstants.getTrackedFunds();
        if (yatCodes.isEmpty()) {
            log.warn("No YAT fund codes configured");
            return;
        }

        int yatSuccess = 0;
        int yatFail = 0;
        for (String code : yatCodes) {
            try {
                Thread.sleep(2000);
                TefasResponse response = tefasClient.fetchFundHistory("YAT", code, today, today);
                if (response != null && response.data() != null && !response.data().isEmpty()) {
                    Fund saved = self.saveFundSnapshot(response.data().getFirst(), "YAT");
                    fundCacheService.putSnapshot(saved.getFundCode(), saved);
                    yatSuccess++;
                }
            } catch (Exception e) {
                yatFail++;
                log.error("Failed to save YAT snapshot {}: {}", code, e.getMessage(), e);
            }
        }
        log.info("YAT snapshots done: {} success, {} failed", yatSuccess, yatFail);
        log.info("Total snapshot update: {} success, {} failed", successCount + yatSuccess, failCount + yatFail);
    }

    @Transactional
    public Fund saveFundSnapshot(TefasResponse.FundData dto, String fundType) {
        LocalDateTime now = LocalDateTime.now();
        Fund fund = fundRepository.findById(dto.fonKodu()).orElse(null);
        if (fund != null) {
            fundMapper.updateEntity(fund, dto, fundType, now);
        } else {
            fund = fundMapper.toEntity(dto, fundType, now);
        }
        fundRepository.save(fund);
        log.debug("Saved snapshot: {} ({}) - {}", dto.fonKodu(), fundType, dto.fiyat());
        return fund;
    }

    public void updateFundCandles() {
        log.info("Starting fund candle update...");

        updateCandlesForType("BYF");
        updateCandlesForType("YAT");

        log.info("Fund candle update completed");
    }

    private void updateCandlesForType(String fundType) {
        List<Fund> funds;
        if ("BYF".equals(fundType)) {
            funds = fundRepository.findAll().stream()
                    .filter(f -> "BYF".equals(f.getFundType()))
                    .toList();
        } else {
            List<String> yatCodes = marketConstants.getTrackedFunds();
            funds = fundRepository.findAllById(yatCodes);
        }

        if (funds.isEmpty()) {
            log.warn("No {} funds found in DB to fetch candles for", fundType);
            return;
        }

        log.info("Starting {} candle update for {} funds", fundType, funds.size());
        int successCount = 0;
        int failCount = 0;

        for (Fund fund : funds) {
            try {
                Thread.sleep(2000);
                long existingCount = fundCandleRepository.countByFundCode(fund.getFundCode());

                if (existingCount >= MIN_CANDLES_FOR_INCREMENTAL) {
                    int saved = self.fetchAndSaveTodayCandle(fund, fundType);
                    log.info("{} ({}) - Incremental: {} candle saved", fund.getFundCode(), fundType, saved);
                } else {
                    int saved = fetchAndSaveFullHistory(fund, fundType);
                    log.info("{} ({}) - Full history: {} candles saved", fund.getFundCode(), fundType, saved);
                }

                fundCacheService.refreshHistory(fund.getFundCode());
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("Failed candle update for {} ({}): {}", fund.getFundCode(), fundType, e.getMessage());
            }
        }
        log.info("{} candle update: {} success, {} failed", fundType, successCount, failCount);
    }

    @Transactional
    public int fetchAndSaveTodayCandle(Fund fund, String fundType) {
        LocalDate today = findLastBusinessDay(LocalDate.now());
        TefasResponse response = tefasClient.fetchFundHistory(fundType, fund.getFundCode(), today, today);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            return 0;
        }

        int saved = 0;
        for (TefasResponse.FundData dto : response.data()) {
            LocalDateTime candleDate = fundMapper.parseTimestamp(dto.tarih());
            FundCandle existing = fundCandleRepository
                    .findByFundCodeAndCandleDate(fund.getFundCode(), candleDate)
                    .orElse(null);

            if (existing != null) {
                fundMapper.updateCandleEntity(existing, dto);
                fundCandleRepository.save(existing);
            } else {
                fundCandleRepository.save(fundMapper.toCandleEntity(dto, fund, fundType));
            }
            saved++;
        }
        return saved;
    }

    private int fetchAndSaveFullHistory(Fund fund, String fundType) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(YEARS_TO_FETCH);
        int totalSaved = 0;

        LocalDate windowStart = startDate;
        while (windowStart.isBefore(endDate)) {
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            int saved = -1;
            int usedWindowSize = SKIP_DAYS;

            for (int windowSize : WINDOW_SIZES) {
                LocalDate windowEnd = windowStart.plusDays(windowSize - 1);
                if (windowEnd.isAfter(endDate)) {
                    windowEnd = endDate;
                }

                saved = tryFetchWindow(fund, fundType, windowStart, windowEnd);

                if (saved >= 0) {
                    usedWindowSize = windowSize;
                    break;
                }

                log.debug("{} - {}-day window failed: {} to {}",
                        fund.getFundCode(), windowSize, windowStart, windowEnd);

                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }

            if (saved < 0) {
                log.debug("{} - All window sizes failed, skipping {} days from {}",
                        fund.getFundCode(), SKIP_DAYS, windowStart);
                windowStart = windowStart.plusDays(SKIP_DAYS);
            } else {
                totalSaved += saved;
                windowStart = windowStart.plusDays(usedWindowSize);
            }
        }

        LocalDate fiveYearsAgo = endDate.minusYears(YEARS_TO_FETCH);
        fundCandleRepository.deleteByFundCodeAndCandleDateBefore(
                fund.getFundCode(), fiveYearsAgo.atStartOfDay());

        log.info("{} ({}) - Full history complete: {} candles", fund.getFundCode(), fundType, totalSaved);
        return totalSaved;
    }

    private int tryFetchWindow(Fund fund, String fundType, LocalDate start, LocalDate end) {
        try {
            TefasResponse response = tefasClient.fetchFundHistory(fundType, fund.getFundCode(), start, end);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                return 0;
            }

            int saved = self.saveCandleBatch(fund, fundType, response.data());
            log.debug("{} - Window {} to {}: {} candles", fund.getFundCode(), start, end, saved);
            return saved;
        } catch (Exception e) {
            log.warn("{} - Window {} to {} failed: {}", fund.getFundCode(), start, end, e.getMessage());
            return -1;
        }
    }

    @Transactional
    public int saveCandleBatch(Fund fund, String fundType, List<TefasResponse.FundData> dtos) {
        Map<LocalDateTime, FundCandle> existingMap = fundCandleRepository
                .findByFundCodeOrderByCandleDateDesc(fund.getFundCode())
                .stream()
                .collect(Collectors.toMap(
                        FundCandle::getCandleDate,
                        Function.identity(),
                        (a, b) -> a));

        List<FundCandle> toSave = new ArrayList<>(dtos.size());
        int newCount = 0;
        int updateCount = 0;

        for (TefasResponse.FundData dto : dtos) {
            LocalDateTime candleDate = fundMapper.parseTimestamp(dto.tarih());
            FundCandle existing = existingMap.get(candleDate);

            if (existing != null) {
                fundMapper.updateCandleEntity(existing, dto);
                toSave.add(existing);
                updateCount++;
            } else {
                toSave.add(fundMapper.toCandleEntity(dto, fund, fundType));
                newCount++;
            }
        }

        if (!toSave.isEmpty()) {
            fundCandleRepository.saveAll(toSave);
        }

        log.debug("{} - Batch: {} new, {} updated", fund.getFundCode(), newCount, updateCount);
        return toSave.size();
    }
}
