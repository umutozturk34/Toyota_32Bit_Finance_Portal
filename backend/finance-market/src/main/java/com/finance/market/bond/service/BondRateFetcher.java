package com.finance.market.bond.service;


import com.finance.market.bond.client.EvdsBondClient;
import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.external.BondRateItemDto;
import com.finance.common.exception.BusinessException;
import com.finance.market.bond.mapper.EvdsBondClientMapper;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.util.BondSerieFilterUtil;
import com.finance.market.core.client.AbstractEvdsClient;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.util.WindowedFetchPlanner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch-fetches bond coupon-rate/price history from EVDS across multiple bonds at once: it unions
 * each bond's date window, queries the combined .ORAN/price codes window-by-window, and maps results
 * back per ISIN (skipping out-of-range and already-stored dates). Throws only if every window fails.
 */
@Component
@Log4j2
public class BondRateFetcher {

    /** One bond's rate-history fetch target: its codes, entity, and date window to fill. */
    public record BondHistoryTarget(String isinCode, String priceCode, Bond bond,
                                     LocalDate startDate, LocalDate endDate) {}

    private static final DateTimeFormatter EVDS_DATE_FMT = AbstractEvdsClient.DATE_FMT;

    private final EvdsBondClient evdsClient;
    private final EvdsBondClientMapper clientMapper;
    private final BondRateHistoryRepository rateHistoryRepository;
    private final int maxDaysPerRequest;

    public BondRateFetcher(EvdsBondClient evdsClient,
                           EvdsBondClientMapper clientMapper,
                           BondRateHistoryRepository rateHistoryRepository,
                           BondProperties bondProperties) {
        this.evdsClient = evdsClient;
        this.clientMapper = clientMapper;
        this.rateHistoryRepository = rateHistoryRepository;
        this.maxDaysPerRequest = bondProperties.getMaxDaysPerRequest();
    }

    /** Fetches rate history for all targets, returning new (unstored) records keyed by ISIN. */
    public Map<String, List<BondRateHistory>> fetchBatch(List<BondHistoryTarget> targets) {
        Map<String, List<BondRateHistory>> byIsin = new HashMap<>();
        if (targets == null || targets.isEmpty()) return byIsin;

        List<BondHistoryTarget> active = new ArrayList<>();
        for (BondHistoryTarget t : targets) {
            byIsin.put(t.isinCode(), new ArrayList<>());
            if (t.startDate().isBefore(t.endDate())) active.add(t);
        }
        if (active.isEmpty()) return byIsin;

        LocalDate batchStart = active.stream().map(BondHistoryTarget::startDate).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate batchEnd = active.stream().map(BondHistoryTarget::endDate).max(Comparator.naturalOrder()).orElseThrow();

        List<String> codes = new ArrayList<>(active.size() * 2);
        for (BondHistoryTarget t : active) {
            codes.add(BondSerieFilterUtil.toOranCode(t.isinCode()));
            if (t.priceCode() != null) codes.add(t.priceCode());
        }

        List<WindowedFetchPlanner.DateWindow> windows = WindowedFetchPlanner
                .planForward(batchStart, batchEnd, maxDaysPerRequest);
        log.debug("Fetching batched rate history: {} bonds, {} codes, {} windows ({} → {})",
                active.size(), codes.size(), windows.size(), batchStart, batchEnd);

        int successWindows = 0;
        int failedWindows = 0;
        for (int i = 0; i < windows.size(); i++) {
            try {
                WindowedFetchPlanner.DateWindow w = windows.get(i);
                String start = w.start().format(EVDS_DATE_FMT);
                String end = w.end().format(EVDS_DATE_FMT);
                EvdsDataResponse response = evdsClient.fetchBondData(codes, start, end);
                for (BondHistoryTarget t : active) {
                    String oranCode = BondSerieFilterUtil.toOranCode(t.isinCode());
                    List<BondRateItemDto> items = clientMapper.toRateItemDtos(response, oranCode, t.priceCode());
                    List<BondRateHistory> bucket = byIsin.get(t.isinCode());
                    for (BondRateItemDto item : items) {
                        if (item.rateDate().isBefore(t.startDate()) || item.rateDate().isAfter(t.endDate())) continue;
                        if (rateHistoryRepository.existsByIsinCodeAndRateDate(t.isinCode(), item.rateDate())) continue;
                        bucket.add(BondRateHistory.builder()
                                .bond(t.bond())
                                .rateDate(item.rateDate())
                                .couponRate(item.couponRate())
                                .price(item.price())
                                .build());
                    }
                }
                successWindows++;
            } catch (CallNotPermittedException e) {
                log.error("EVDS circuit breaker OPEN during batch rate fetch at window {}/{}",
                        i + 1, windows.size());
                throw e;
            } catch (Exception e) {
                failedWindows++;
                log.error("Batch rate history window {}/{} failed: {}",
                        i + 1, windows.size(), e.getMessage());
            }
        }

        if (successWindows == 0 && failedWindows > 0) {
            throw new BusinessException(
                    "All " + failedWindows + " batched rate history windows failed");
        }
        return byIsin;
    }

}
