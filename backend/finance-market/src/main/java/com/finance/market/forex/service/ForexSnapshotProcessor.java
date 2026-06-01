package com.finance.market.forex.service;

import com.finance.common.exception.ExternalApiException;
import com.finance.market.core.client.AbstractEvdsClient;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.service.MarketSnapshotProcessor;
import com.finance.market.core.util.ApiAssetValidator;
import com.finance.market.core.util.TrackedRefreshRunner;
import com.finance.market.forex.client.EvdsForexClient;
import com.finance.market.forex.config.ForexProperties;
import com.finance.market.forex.mapper.ForexEvdsMapper;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.shared.util.CodeNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Applies a single forex currency's latest EVDS window: upserts the recent candles, sets the latest
 * snapshot price (unit-normalized), and computes change from the two most recent stored candles.
 * Also backs single-currency refresh and existence validation.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ForexSnapshotProcessor implements MarketSnapshotProcessor {

    private static final DateTimeFormatter EVDS_DATE_FMT = AbstractEvdsClient.DATE_FMT;

    private final EvdsForexClient evdsClient;
    private final EvdsForexCurrencyResolver currencyResolver;
    private final ForexEvdsMapper evdsMapper;
    private final ForexEntityWriter entityWriter;
    private final ForexCandleRepository forexCandleRepository;
    private final TransactionTemplate transactionTemplate;
    private final ForexProperties forexProperties;

    /** Persists candles from the window then the latest-row snapshot with computed change; cache-backed. */
    public Forex applyLatestSnapshot(ForexSerieMetadata meta, EvdsDataResponse response) {
        Forex forex = entityWriter.upsertForexShell(meta);
        List<ForexCandle> windowCandles = evdsMapper.toCandles(forex, meta, response, entityWriter.getScale());
        if (!windowCandles.isEmpty()) {
            entityWriter.upsertCandles(forex, windowCandles);
        }
        ForexEvdsMapper.ItemRow latest = evdsMapper.extractLatestRow(response, meta);
        if (latest == null) {
            return entityWriter.saveSnapshot(forex);
        }
        forex.applyEvdsSnapshot(latest.candleDate(),
                latest.buyingRaw(), latest.sellingRaw(),
                latest.effectiveBuyingRaw(), latest.effectiveSellingRaw(),
                meta.unit(), entityWriter.getScale());
        applyChangeFromCandles(forex);
        return entityWriter.saveSnapshot(forex);
    }

    public Optional<LocalDate> findLastCandleDate(String currencyCode) {
        return forexCandleRepository.findFirstByCurrencyCodeOrderByCandleDateDesc(currencyCode)
                .map(c -> c.getCandleDate().toLocalDate());
    }

    @Override
    public void refreshOne(String currencyCode) {
        TrackedRefreshRunner.refreshSnapshot(currencyCode, CodeNormalizer::upper, normalized -> {
            ForexSerieMetadata meta = currencyResolver
                    .resolveActive(evdsClient.fetchDovizSerieList(), evdsClient.fetchEfektifSerieList())
                    .stream()
                    .filter(m -> m.currencyCode().equals(normalized))
                    .findFirst()
                    .orElse(null);
            if (meta == null) {
                log.warn("Refresh requested for unknown forex currency: {}", normalized);
                return false;
            }
            EvdsDataResponse response = fetchLatestWindow(meta);
            if (response == null) return false;
            transactionTemplate.executeWithoutResult(status -> applyLatestSnapshot(meta, response));
            return true;
        }, log, "forex");
    }

    @Override
    public boolean exists(String currencyCode) {
        return ApiAssetValidator.validate(currencyCode, true,
                normalized -> currencyResolver.isActiveCurrencyCode(evdsClient.fetchDovizSerieList(), normalized),
                log, "Forex");
    }

    private EvdsDataResponse fetchLatestWindow(ForexSerieMetadata meta) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(forexProperties.getLatestLookbackDays());
        try {
            return evdsClient.fetchForexData(meta.seriesCodes(),
                    from.format(EVDS_DATE_FMT), today.format(EVDS_DATE_FMT));
        } catch (ExternalApiException ex) {
            log.error("Forex snapshot fetch failed for {}: {}", meta.currencyCode(), ex.getMessage());
            return null;
        }
    }

    /**
     * Sets day change from the candle immediately preceding the latest stored candle. Anchoring on
     * the latest stored date (rather than today) keeps the result meaningful on weekends and
     * holidays: if the most recent data point is a Friday close, change is computed between Friday
     * and the previous trading day instead of collapsing to zero against Friday itself.
     */
    private void applyChangeFromCandles(Forex forex) {
        if (forex.getSellingPrice() == null) return;
        Optional<ForexCandle> latestCandle = forexCandleRepository
                .findFirstByCurrencyCodeOrderByCandleDateDesc(forex.getCurrencyCode());
        if (latestCandle.isEmpty()) return;
        BigDecimal previous = forexCandleRepository
                .findFirstByCurrencyCodeAndCandleDateBeforeOrderByCandleDateDesc(
                        forex.getCurrencyCode(), latestCandle.get().getCandleDate())
                .map(ForexCandle::getSellingPrice)
                .orElse(null);
        if (previous == null) return;
        forex.applyChange(forex.getSellingPrice(), previous, entityWriter.getScale());
    }
}
