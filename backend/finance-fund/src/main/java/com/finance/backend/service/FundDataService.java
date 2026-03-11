package com.finance.backend.service;

import com.finance.backend.client.TefasClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.constants.MarketConstants;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.FundMapper;
import com.finance.backend.util.BatchFailureGuard;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.repository.FundCandleRepository;
import com.finance.backend.repository.FundRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@Service
public class FundDataService {

    private final TefasClient tefasClient;
    private final FundMapper fundMapper;
    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final MarketConstants marketConstants;
    private final TransactionTemplate transactionTemplate;
    private final int windowSize;
    private final int minCandlesForIncremental;
    private final int yearsToFetch;
    private final ZoneId appZone;

    public FundDataService(TefasClient tefasClient,
            FundMapper fundMapper,
            FundRepository fundRepository,
            FundCandleRepository fundCandleRepository,
            MarketCacheService<Fund, FundCandle> fundCacheService,
            MarketConstants marketConstants,
            PlatformTransactionManager transactionManager,
            AppProperties appProperties) {
        this.tefasClient = tefasClient;
        this.fundMapper = fundMapper;
        this.fundRepository = fundRepository;
        this.fundCandleRepository = fundCandleRepository;
        this.fundCacheService = fundCacheService;
        this.marketConstants = marketConstants;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        AppProperties.Fund fundConfig = appProperties.getFund();
        this.windowSize = fundConfig.getWindowSizes();
        this.minCandlesForIncremental = fundConfig.getMinCandlesForIncremental();
        this.yearsToFetch = fundConfig.getYearsToFetch();
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    private LocalDate findLastBusinessDay(LocalDate from) {
        var istanbulNow = java.time.ZonedDateTime.now(appZone);
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
        log.info("Starting fund snapshot update for {}", today);
        int successCount = 0;
        int failCount = 0;
        List<String> failedCodes = new ArrayList<>();

        try {
            List<TefasFundDto> byfFunds = fetchTefas("BYF", null, today, today);
            for (TefasFundDto dto : byfFunds) {
                try {
                    Fund saved = transactionTemplate.execute(status -> saveFundSnapshot(dto, "BYF"));
                    fundCacheService.putSnapshot(saved.getFundCode(), saved);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    failedCodes.add(dto.fundCode());
                    log.error("BYF snapshot failed {}: {}", dto.fundCode(), e.getMessage(), e);
                    BatchFailureGuard.check(successCount, failCount, failedCodes, "BYF snapshot");
                }
            }
        } catch (CallNotPermittedException e) {
            log.warn("TEFAS circuit breaker is OPEN, aborting snapshot update");
            return;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch BYF funds", e);
        }

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
                List<TefasFundDto> yatFunds = fetchTefas("YAT", code, today, today);
                if (!yatFunds.isEmpty()) {
                    Fund saved = transactionTemplate.execute(status -> saveFundSnapshot(yatFunds.getFirst(), "YAT"));
                    fundCacheService.putSnapshot(saved.getFundCode(), saved);
                    yatSuccess++;
                }
            } catch (CallNotPermittedException e) {
                log.warn("TEFAS circuit breaker is OPEN, stopping YAT snapshot update");
                break;
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

    private Fund saveFundSnapshot(TefasFundDto dto, String fundType) {
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

    public void updateFundCandles() {
        log.info("Starting fund candle update");
        pruneOldCandles();
        updateCandlesForType("BYF");
        updateCandlesForType("YAT");
    }

    private void pruneOldCandles() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(yearsToFetch);
        transactionTemplate.executeWithoutResult(status -> fundCandleRepository.deleteByCandleDateBefore(cutoffDate));
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
                long existingCount = fundCandleRepository.countByFundCode(fund.getFundCode());

                if (existingCount >= minCandlesForIncremental) {
                    transactionTemplate.execute(status -> fetchAndSaveSinceLastCandle(fund, fundType));
                } else {
                    fetchAndSaveFullHistory(fund, fundType);
                }

                fundCacheService.refreshHistory(fund.getFundCode());
                successCount++;
            } catch (CallNotPermittedException e) {
                log.warn("TEFAS circuit breaker is OPEN, stopping {} candle update after {} success, {} failed",
                        fundType, successCount, failCount);
                break;
            } catch (Exception e) {
                failCount++;
                failedFunds.add(fund.getFundCode());
                log.error("Failed candle update for {} ({}): {}", fund.getFundCode(), fundType, e.getMessage(), e);
                BatchFailureGuard.check(successCount, failCount, failedFunds, fundType + " candle");
            }
        }
        log.info("{} candle update: {} success, {} failed", fundType, successCount, failCount);
    }

    private int fetchAndSaveSinceLastCandle(Fund fund, String fundType) {
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

    private int fetchAndSaveFullHistory(Fund fund, String fundType) {
        LocalDate finalEndDate = findLastBusinessDay(LocalDate.now(appZone));
        LocalDate limitDate = finalEndDate.minusYears(yearsToFetch);
        int totalSaved = 0;
        LocalDate currentWindowEnd = finalEndDate;

        while (currentWindowEnd.isAfter(limitDate)) {
            LocalDate currentWindowStart = currentWindowEnd.minusDays(windowSize);

            if (currentWindowStart.isBefore(limitDate)) {
                currentWindowStart = limitDate;
            }

            int saved = tryFetchWindow(fund, fundType, currentWindowStart, currentWindowEnd);

            if (saved > 0) {
                totalSaved += saved;
                currentWindowEnd = currentWindowStart.minusDays(1);
            } else {
                currentWindowEnd = currentWindowStart.minusDays(1);
            }

            if (totalSaved > 0 && saved <= 0 && currentWindowEnd.isBefore(finalEndDate.minusYears(1))) {
                break;
            }
        }

        log.info("{} full history: {} candles", fund.getFundCode(), totalSaved);
        return totalSaved;
    }

    private int tryFetchWindow(Fund fund, String fundType, LocalDate start, LocalDate end) {
        try {
            List<TefasFundDto> candles = fetchTefas(fundType, fund.getFundCode(), start, end);

            if (candles.isEmpty()) {
                return 0;
            }

            int saved = transactionTemplate.execute(status -> saveCandleBatch(fund, fundType, candles));
            log.debug("{} - Window {} to {}: {} candles", fund.getFundCode(), start, end, saved);
            return saved;
        } catch (CallNotPermittedException e) {
            log.warn("{} - TEFAS circuit breaker is OPEN, skipping", fund.getFundCode());
            throw e;
        } catch (Exception e) {
            log.warn("{} - Window {} to {} failed: {}", fund.getFundCode(), start, end, e.getMessage(), e);
            return -1;
        }
    }

    private int saveCandleBatch(Fund fund, String fundType, List<TefasFundDto> dtos) {
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

    private List<TefasFundDto> fetchTefas(String fundType, String fundCode,
            LocalDate startDate, LocalDate endDate) {
        List<TefasFundDto> result = tefasClient.post(fundType, fundCode, startDate, endDate);
        if (result == null) {
            throw new ExternalApiException("TEFAS",
                    "Non-JSON response for " + fundType + " " + (fundCode != null ? fundCode : "all"));
        }
        return result;
    }
}
