    package com.finance.backend.service;

    import com.finance.backend.client.TefasClient;
    import com.finance.backend.constants.MarketConstants;
    import com.finance.backend.dto.external.TefasFundDto;
    import com.finance.backend.exception.BusinessException;
    import com.finance.backend.mapper.FundMapper;
    import com.finance.backend.util.BatchFailureGuard;
    import com.finance.backend.model.Fund;
    import com.finance.backend.model.FundCandle;
    import com.finance.backend.repository.FundCandleRepository;
    import com.finance.backend.repository.FundRepository;
    import lombok.extern.log4j.Log4j2;
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

    @Log4j2
    @Service
    public class FundDataService {

        private static final int[] WINDOW_SIZES = {65, 60, 30, 15};
        private static final int SKIP_DAYS = 15;
        private static final int MIN_CANDLES_FOR_INCREMENTAL = 30;
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
            int successCount = 0;
            int failCount = 0;
            List<String> failedCodes = new ArrayList<>();

            try {
                List<TefasFundDto> byfFunds = tefasClient.fetchAllByfFunds(today);
                for (TefasFundDto dto : byfFunds) {
                        try {
                            Fund saved = self.saveFundSnapshot(dto, "BYF");
                            fundCacheService.putSnapshot(saved.getFundCode(), saved);
                            successCount++;
                        } catch (Exception e) {
                            failCount++;
                            failedCodes.add(dto.fundCode());
                            log.error("BYF snapshot failed {}: {}", dto.fundCode(), e.getMessage(), e);
                            BatchFailureGuard.check(successCount, failCount, failedCodes, "BYF snapshot");
                        }
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to fetch BYF funds", e);
            }

            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            List<String> yatCodes = marketConstants.getTrackedFunds();
            if (yatCodes.isEmpty()) {
                log.warn("No YAT fund codes configured");
                return;
            }

            int yatSuccess = 0;
            int yatFail = 0;
            List<String> yatFailedCodes = new ArrayList<>();
            for (String code : yatCodes) {
                try {
                    Thread.sleep(2000);
                    List<TefasFundDto> yatFunds = tefasClient.fetchFundHistory("YAT", code, today, today);
                    if (!yatFunds.isEmpty()) {
                        Fund saved = self.saveFundSnapshot(yatFunds.getFirst(), "YAT");
                        fundCacheService.putSnapshot(saved.getFundCode(), saved);
                        yatSuccess++;
                    }
                } catch (Exception e) {
                    yatFail++;
                    yatFailedCodes.add(code);
                    log.error("YAT snapshot failed {}: {}", code, e.getMessage(), e);
                    BatchFailureGuard.check(yatSuccess, yatFail, yatFailedCodes, "YAT snapshot");
                }
            }
            log.info("Fund snapshot update: {} success, {} failed", successCount + yatSuccess, failCount + yatFail);
            if (!failedCodes.isEmpty() || !yatFailedCodes.isEmpty()) {
                log.warn("Failed funds: BYF={}, YAT={}", failedCodes, yatFailedCodes);
            }
        }

        @Transactional
        public Fund saveFundSnapshot(TefasFundDto dto, String fundType) {
            LocalDateTime now = LocalDateTime.now();
            Fund fund = fundRepository.findById(dto.fundCode()).orElse(null);
            if (fund != null) {
                fundMapper.updateEntity(fund, dto, fundType, now);
            } else {
                fund = fundMapper.toEntity(dto, fundType, now);
            }
            fundRepository.save(fund);
            log.debug("Saved snapshot: {} ({}) - {}", dto.fundCode(), fundType, dto.price());
            return fund;
        }

        @Transactional
        public void updateFundCandles() {
            pruneOldCandles();
            updateCandlesForType("BYF");
            updateCandlesForType("YAT");
        }

        private void pruneOldCandles() {
            LocalDateTime cutoffDate = LocalDateTime.now().minusYears(YEARS_TO_FETCH);
            fundCandleRepository.deleteByCandleDateBefore(cutoffDate);
        }

        private void updateCandlesForType(String fundType) {
            List<Fund> funds;
            if ("BYF".equals(fundType)) {
                funds = fundRepository.findByFundType("BYF");
            } else {
                List<String> yatCodes = marketConstants.getTrackedFunds();
                funds = fundRepository.findAllById(yatCodes);
            }

            if (funds.isEmpty()) {
                log.warn("No {} funds found", fundType);
                return;
            }

            int successCount = 0;
            int failCount = 0;
            List<String> failedFunds = new ArrayList<>();

            for (Fund fund : funds) {
                try {
                    Thread.sleep(2000);
                    long existingCount = fundCandleRepository.countByFundCode(fund.getFundCode());

                    if (existingCount >= MIN_CANDLES_FOR_INCREMENTAL) {
                        self.fetchAndSaveTodayCandle(fund, fundType);
                    } else {
                        fetchAndSaveFullHistory(fund, fundType);
                    }

                    fundCacheService.refreshHistory(fund.getFundCode());
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    failedFunds.add(fund.getFundCode());
                    log.error("Failed candle update for {} ({}): {}", fund.getFundCode(), fundType, e.getMessage(), e);
                    BatchFailureGuard.check(successCount, failCount, failedFunds, fundType + " candle");
                }
            }
            log.info("{} candle update: {} success, {} failed", fundType, successCount, failCount);
        }

        @Transactional
        public int fetchAndSaveTodayCandle(Fund fund, String fundType) {
            LocalDate today = findLastBusinessDay(LocalDate.now());
            List<TefasFundDto> candles = tefasClient.fetchFundHistory(fundType, fund.getFundCode(), today, today);

            if (candles.isEmpty()) {
                return 0;
            }

            return saveCandleBatch(fund, fundType, candles);
        }

        private int fetchAndSaveFullHistory(Fund fund, String fundType) {
            LocalDate endDate = findLastBusinessDay(LocalDate.now());
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
                } else if (saved == 0) {
                    log.debug("{} - Empty response, skipping {} days from {}",
                            fund.getFundCode(), SKIP_DAYS, windowStart);
                    windowStart = windowStart.plusDays(SKIP_DAYS);
                } else {
                    totalSaved += saved;
                    windowStart = windowStart.plusDays(usedWindowSize);
                }
            }

            log.info("{} full history: {} candles", fund.getFundCode(), totalSaved);
            return totalSaved;
        }

        private int tryFetchWindow(Fund fund, String fundType, LocalDate start, LocalDate end) {
            try {
                List<TefasFundDto> candles = tefasClient.fetchFundHistory(fundType, fund.getFundCode(), start, end);

                if (candles.isEmpty()) {
                    return 0;
                }

                int saved = self.saveCandleBatch(fund, fundType, candles);
                log.debug("{} - Window {} to {}: {} candles", fund.getFundCode(), start, end, saved);
                return saved;
            } catch (Exception e) {
                log.warn("{} - Window {} to {} failed: {}", fund.getFundCode(), start, end, e.getMessage(), e);
                return -1;
            }
        }

        @Transactional
        public int saveCandleBatch(Fund fund, String fundType, List<TefasFundDto> dtos) {
            List<LocalDateTime> dates = dtos.stream().map(TefasFundDto::date).toList();
            Map<LocalDateTime, FundCandle> existingMap = fundCandleRepository
                    .findByFundCodeAndCandleDateIn(fund.getFundCode(), dates)
                    .stream()
                    .collect(Collectors.toMap(
                            FundCandle::getCandleDate,
                            Function.identity(),
                            (a, b) -> a));

            List<FundCandle> toSave = new ArrayList<>(dtos.size());
            int newCount = 0;
            int updateCount = 0;

            for (TefasFundDto dto : dtos) {
                FundCandle existing = existingMap.get(dto.date());

                if (existing != null) {
                    fundMapper.updateCandleEntity(existing, dto);
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
            return newCount + updateCount;
        }
    }
