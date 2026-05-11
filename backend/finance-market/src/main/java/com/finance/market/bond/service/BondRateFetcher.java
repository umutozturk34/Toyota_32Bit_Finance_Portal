package com.finance.market.bond.service;


import com.finance.market.bond.client.EvdsClient;
import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.external.BondRateItemDto;
import com.finance.market.bond.dto.internal.EvdsBondDataResponse;
import com.finance.common.exception.BusinessException;
import com.finance.market.bond.mapper.EvdsBondClientMapper;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.util.BondSerieFilterUtil;
import com.finance.market.core.util.WindowedFetchPlanner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Log4j2
public class BondRateFetcher {

    private static final DateTimeFormatter EVDS_DATE_FMT = EvdsClient.DATE_FMT;

    private final EvdsClient evdsClient;
    private final EvdsBondClientMapper clientMapper;
    private final BondRateHistoryRepository rateHistoryRepository;
    private final int maxDaysPerRequest;

    public BondRateFetcher(EvdsClient evdsClient,
                           EvdsBondClientMapper clientMapper,
                           BondRateHistoryRepository rateHistoryRepository,
                           BondProperties bondProperties) {
        this.evdsClient = evdsClient;
        this.clientMapper = clientMapper;
        this.rateHistoryRepository = rateHistoryRepository;
        this.maxDaysPerRequest = bondProperties.getMaxDaysPerRequest();
    }

    public List<BondRateHistory> fetch(String isinCode, Bond bond, LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            log.debug("Rate history for {} is up to date (start={} >= end={}), skipping", isinCode, startDate, endDate);
            return new ArrayList<>();
        }

        String oranCode = BondSerieFilterUtil.toOranCode(isinCode);
        List<WindowedFetchPlanner.DateWindow> windows = WindowedFetchPlanner
                .planForward(startDate, endDate, maxDaysPerRequest);
        log.debug("Fetching rate history for {}: {} to {} ({} windows)", isinCode, startDate, endDate, windows.size());

        List<BondRateHistory> allRecords = new ArrayList<>();
        int successWindows = 0;
        int failedWindows = 0;
        for (int i = 0; i < windows.size(); i++) {
            try {
                allRecords.addAll(fetchWindow(windows.get(i), oranCode, isinCode, bond));
                successWindows++;
            } catch (CallNotPermittedException e) {
                log.error("EVDS circuit breaker OPEN during rate fetch for {} at window {}/{}",
                        isinCode, i + 1, windows.size());
                throw e;
            } catch (Exception e) {
                failedWindows++;
                log.error("Rate history window {}/{} failed for {}: {}",
                        i + 1, windows.size(), isinCode, e.getMessage());
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

    private List<BondRateHistory> fetchWindow(WindowedFetchPlanner.DateWindow window, String oranCode,
                                               String isinCode, Bond bond) {
        String start = window.start().format(EVDS_DATE_FMT);
        String end = window.end().format(EVDS_DATE_FMT);
        EvdsBondDataResponse response = evdsClient.fetchBondData(List.of(oranCode), start, end);
        List<BondRateItemDto> rateItems = clientMapper.toRateItemDtos(response, oranCode);

        List<BondRateHistory> records = new ArrayList<>(rateItems.size());
        for (BondRateItemDto rateItem : rateItems) {
            if (!rateHistoryRepository.existsByIsinCodeAndRateDate(isinCode, rateItem.rateDate())) {
                records.add(BondRateHistory.builder()
                        .bond(bond)
                        .rateDate(rateItem.rateDate())
                        .couponRate(rateItem.couponRate())
                        .build());
            }
        }
        return records;
    }
}
