package com.finance.market.forex.service;

import com.finance.common.exception.ExternalApiException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.client.AbstractEvdsClient;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.service.MarketRefresher;
import com.finance.market.core.service.TrackedAssetCommandService;
import com.finance.market.core.util.MarketBatchRunner;
import com.finance.market.core.util.WindowedFetchPlanner;
import com.finance.market.forex.client.EvdsForexClient;
import com.finance.market.forex.config.ForexProperties;
import com.finance.market.forex.mapper.ForexEvdsMapper;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.repository.ForexRepository;
import com.finance.shared.util.BatchLogHelper;
import com.finance.shared.util.BatchUpdateRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class ForexUpdateService implements MarketRefresher {

    private static final DateTimeFormatter EVDS_DATE_FMT = AbstractEvdsClient.DATE_FMT;
    private static final int SNAPSHOT_LOOKBACK_DAYS = 5;
    private static final int BACKFILL_WINDOW_DAYS = 4 * 365;
    private static final int EVDS_ROW_CAP = 1000;

    private final EvdsForexClient evdsClient;
    private final EvdsForexCurrencyResolver currencyResolver;
    private final ForexSnapshotProcessor snapshotProcessor;
    private final ForexEntityWriter entityWriter;
    private final ForexEvdsMapper evdsMapper;
    private final ForexRepository forexRepository;
    private final TrackedAssetCommandService trackedAssetCommandService;
    private final ForexProperties forexProperties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public MarketType getMarketType() {
        return MarketType.FOREX;
    }

    @Override
    public void refreshAll() {
        List<ForexSerieMetadata> candidates = currencyResolver.resolveActive(
                evdsClient.fetchDovizSerieList(),
                evdsClient.fetchEfektifSerieList());
        if (candidates.isEmpty()) {
            log.warn("No active forex currencies returned from EVDS, skipping refresh");
            return;
        }

        EvdsDataResponse snapshotResponse = fetchSnapshotBatch(candidates);
        if (snapshotResponse == null) {
            log.error("Forex snapshot batch failed, aborting refresh");
            return;
        }

        List<ForexSerieMetadata> active = candidates.stream()
                .filter(meta -> evdsMapper.extractLatestRow(snapshotResponse, meta) != null)
                .toList();
        if (active.size() < candidates.size()) {
            log.info("Forex smoke filter dropped {} dead currencies (no recent data)",
                    candidates.size() - active.size());
        }
        if (active.isEmpty()) {
            log.warn("Forex smoke filter dropped all candidates, skipping refresh");
            return;
        }
        active.forEach(meta -> transactionTemplate.executeWithoutResult(status -> autoTrack(meta)));

        Map<String, LocalDate> lastBeforePhaseOne = snapshotLastCandleDates(active);

        BatchUpdateRunner.Result snapshotResult = MarketBatchRunner.run(active,
                meta -> transactionTemplate.executeWithoutResult(status ->
                        snapshotProcessor.applyLatestSnapshot(meta, snapshotResponse)),
                ForexSerieMetadata::currencyCode,
                log, "Forex", "snapshot", forexProperties.getBatchMinSample());
        BatchLogHelper.logSummary(log, "Forex snapshot update", snapshotResult);

        LocalDate snapshotWindowStart = LocalDate.now().minusDays(SNAPSHOT_LOOKBACK_DAYS);
        BatchUpdateRunner.Result backfillResult = MarketBatchRunner.run(active,
                meta -> fillGapBelowSnapshotWindow(meta, lastBeforePhaseOne.get(meta.currencyCode()), snapshotWindowStart),
                ForexSerieMetadata::currencyCode,
                log, "Forex", "backfill", forexProperties.getBatchMinSample());
        BatchLogHelper.logSummary(log, "Forex candle backfill", backfillResult);
    }

    @Override
    public void refresh(String currencyCode) {
        snapshotProcessor.refreshOne(currencyCode);
    }

    public boolean isActiveCurrency(String code) {
        if (code == null || code.isBlank()) return false;
        return currencyResolver.isActiveCurrencyCode(evdsClient.fetchDovizSerieList(), code);
    }

    private void autoTrack(ForexSerieMetadata meta) {
        trackedAssetCommandService.autoTrack(TrackedAssetType.FOREX,
                meta.currencyCode(), meta.displayNameTr(), 0);
    }

    private Map<String, LocalDate> snapshotLastCandleDates(List<ForexSerieMetadata> active) {
        Map<String, LocalDate> result = new HashMap<>();
        for (ForexSerieMetadata meta : active) {
            snapshotProcessor.findLastCandleDate(meta.currencyCode())
                    .ifPresent(date -> result.put(meta.currencyCode(), date));
        }
        return result;
    }

    private EvdsDataResponse fetchSnapshotBatch(List<ForexSerieMetadata> active) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(SNAPSHOT_LOOKBACK_DAYS);
        List<String> allCodes = active.stream()
                .flatMap(m -> m.seriesCodes().stream())
                .toList();
        return fetchData(allCodes, from, today);
    }

    private void fillGapBelowSnapshotWindow(ForexSerieMetadata meta, LocalDate lastBefore, LocalDate snapshotWindowStart) {
        Forex forex = forexRepository.findById(meta.currencyCode()).orElse(null);
        if (forex == null) return;
        LocalDate gapEnd = snapshotWindowStart.minusDays(1);
        LocalDate gapStart = lastBefore == null
                ? forexProperties.getBackfillStartDate()
                : lastBefore.plusDays(1);
        if (gapStart.isAfter(gapEnd)) return;
        backfillRange(forex, meta, gapStart, gapEnd);
    }

    private void backfillRange(Forex forex, ForexSerieMetadata meta, LocalDate floor, LocalDate ceiling) {
        List<WindowedFetchPlanner.DateWindow> windows = WindowedFetchPlanner.planBackward(floor, ceiling, BACKFILL_WINDOW_DAYS);
        for (WindowedFetchPlanner.DateWindow window : windows) {
            EvdsDataResponse response = fetchData(meta.seriesCodes(), window.start(), window.end());
            if (response == null) break;
            transactionTemplate.executeWithoutResult(status ->
                    entityWriter.upsertCandles(forex, evdsMapper.toCandles(forex, meta, response, entityWriter.getScale())));
            if (response.totalCount() < EVDS_ROW_CAP) break;
            LocalDate earliest = evdsMapper.extractEarliestDate(response);
            if (earliest == null || !earliest.isAfter(floor)) break;
        }
    }

    private EvdsDataResponse fetchData(List<String> serieCodes, LocalDate from, LocalDate to) {
        try {
            return evdsClient.fetchForexData(serieCodes,
                    from.format(EVDS_DATE_FMT), to.format(EVDS_DATE_FMT));
        } catch (ExternalApiException ex) {
            log.error("Forex EVDS fetch failed ({} → {}): {}", from, to, ex.getMessage());
            return null;
        }
    }
}
