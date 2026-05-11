package com.finance.market.fund.service;


import com.finance.market.fund.client.TefasClient;
import com.finance.market.fund.dto.external.TefasFundDto;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundType;
import com.finance.market.core.util.WindowedFetchPlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class FundBulkFetchExecutor {

    private final TefasClient tefasClient;
    private final TransactionTemplate transactionTemplate;

    public BulkRunResult runWindows(FundType fundType,
                                     List<WindowedFetchPlanner.DateWindow> windows,
                                     Map<String, Fund> trackedByCode,
                                     BiFunction<Fund, List<TefasFundDto>, Integer> saver) {
        if (windows.isEmpty()) {
            return BulkRunResult.empty();
        }
        log.info("Bulk {} fetch: {} tracked funds, {} windows", fundType,
                trackedByCode.size(), windows.size());

        int totalSaved = 0;
        int processed = 0;
        for (WindowedFetchPlanner.DateWindow window : windows) {
            totalSaved += processWindow(fundType, window, trackedByCode, saver);
            processed++;
        }
        log.info("Bulk {} done: {} candles saved across {} windows", fundType, totalSaved, processed);
        return new BulkRunResult(totalSaved, processed);
    }

    private int processWindow(FundType fundType, WindowedFetchPlanner.DateWindow window,
                               Map<String, Fund> trackedByCode,
                               BiFunction<Fund, List<TefasFundDto>, Integer> saver) {
        long start = System.currentTimeMillis();
        List<TefasFundDto> bulk = tefasClient.bulkFetch(fundType, window.start(), window.end());
        Map<Fund, List<TefasFundDto>> grouped = groupByTrackedFund(bulk, trackedByCode);
        int saved = persistGrouped(fundType, grouped, saver);
        log.info("[TIMING] {} bulk window {}-{}: {} bulk rows, {} tracked matched, {} saved, {}ms",
                fundType, window.start(), window.end(), bulk.size(), grouped.size(),
                saved, System.currentTimeMillis() - start);
        return saved;
    }

    private Map<Fund, List<TefasFundDto>> groupByTrackedFund(List<TefasFundDto> bulk,
                                                              Map<String, Fund> trackedByCode) {
        if (bulk.isEmpty() || trackedByCode.isEmpty()) {
            return Collections.emptyMap();
        }
        return bulk.stream()
                .filter(dto -> trackedByCode.containsKey(dto.fundCode()))
                .collect(Collectors.groupingBy(dto -> trackedByCode.get(dto.fundCode())));
    }

    private int persistGrouped(FundType fundType, Map<Fund, List<TefasFundDto>> grouped,
                                BiFunction<Fund, List<TefasFundDto>, Integer> saver) {
        int saved = 0;
        for (Map.Entry<Fund, List<TefasFundDto>> entry : grouped.entrySet()) {
            Fund fund = entry.getKey();
            try {
                int count = transactionTemplate.execute(s -> saver.apply(fund, entry.getValue()));
                if (count > 0) saved += count;
            } catch (Exception e) {
                log.error("Failed to save {} candle batch for fund {}",
                        fundType, fund.getFundCode(), e);
            }
        }
        return saved;
    }

    public record BulkRunResult(int totalSaved, int windowsProcessed) {
        public static BulkRunResult empty() { return new BulkRunResult(0, 0); }
    }
}
