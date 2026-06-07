package com.finance.market.commodity.service;

import com.finance.market.core.service.ExchangeRateProvider;

import com.finance.market.core.service.ExchangeRateSnapshot;

import com.finance.market.core.service.MarketRefresher;

import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.market.commodity.config.CommodityProperties;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.shared.util.BatchLogHelper;
import com.finance.shared.util.BatchUpdateRunner;
import com.finance.market.core.util.MarketBatchRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Drives the commodity refresh: loads the shared USD/TRY history once, then updates each tracked
 * commodity that has a Yahoo symbol mapping (per-item failures isolated). Aborts when no symbols map
 * or USD/TRY rates are unavailable, since TRY prices cannot be synthesised without them.
 */
@Log4j2
@Service
public class CommodityUpdateService implements MarketRefresher {

    private final CommoditySnapshotProcessor snapshotProcessor;
    private final ExchangeRateProvider exchangeRateProvider;
    private final PreciousMetalDerivativeCalculator derivativeCalculator;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final YahooSymbolResolver yahooSymbolResolver;
    private final int batchMinSample;

    /**
     * Reads the minimum-sample batch threshold from {@link CommodityProperties} (below which a batch run
     * is treated as a failed sample) and stores the collaborators for the refresh pipeline.
     */
    public CommodityUpdateService(CommoditySnapshotProcessor snapshotProcessor,
                                  ExchangeRateProvider exchangeRateProvider,
                                  PreciousMetalDerivativeCalculator derivativeCalculator,
                                  TrackedAssetQueryService trackedAssetQueryService,
                                  YahooSymbolResolver yahooSymbolResolver,
                                  CommodityProperties commodityProperties) {
        this.snapshotProcessor = snapshotProcessor;
        this.exchangeRateProvider = exchangeRateProvider;
        this.derivativeCalculator = derivativeCalculator;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.yahooSymbolResolver = yahooSymbolResolver;
        this.batchMinSample = commodityProperties.getBatchMinSample();
    }

    /** Identifies this refresher as handling {@link MarketType#COMMODITY}. */
    @Override
    public MarketType getMarketType() {
        return MarketType.COMMODITY;
    }

    /**
     * Refreshes every tracked commodity that has a Yahoo symbol mapping. Loads the USD/TRY history once
     * and shares it across all items (TRY prices are synthesised from USD). No-op when no commodities are
     * tracked; per-item failures are isolated by the batch runner.
     *
     * @throws BusinessException if commodities are tracked but none have a Yahoo symbol mapping
     * @throws ExternalApiException if USD/TRY rates are unavailable (TRY prices cannot be synthesised)
     */
    @Override
    public void refreshAll() {
        List<String> enabledCodes = trackedAssetQueryService.getCodes(TrackedAssetType.COMMODITY);
        if (enabledCodes.isEmpty()) {
            log.info("No tracked commodities enabled, nothing to sync");
            return;
        }
        List<String> fetchableCodes = enabledCodes.stream()
                .filter(code -> yahooSymbolResolver.resolve(code) != null)
                .toList();
        if (fetchableCodes.isEmpty()) {
            log.error("Tracked commodities exist ({}) but none have Yahoo symbol mappings", enabledCodes.size());
            throw new BusinessException(
                    "Tracked commodities exist but none have Yahoo symbol mappings (check yahoo-symbol-overrides yaml)");
        }

        Map<String, java.math.BigDecimal> usdtryRateMap = exchangeRateProvider.getUsdTryHistory();
        if (usdtryRateMap.isEmpty()) {
            log.error("USDTRY rates unavailable, cannot synthesise commodity TRY prices");
            throw new ExternalApiException("Yahoo Finance",
                    "USDTRY rates unavailable, cannot synthesise commodity TRY prices");
        }
        ExchangeRateSnapshot usdTry = exchangeRateProvider.getCurrentUsdTry();

        log.info("Starting commodity sync for {} items", fetchableCodes.size());

        BatchUpdateRunner.Result result = MarketBatchRunner.run(
                fetchableCodes,
                code -> {
                    snapshotProcessor.updateOne(code, usdtryRateMap, usdTry);
                },
                code -> code,
                log, "Commodity", "update", batchMinSample);

        BatchLogHelper.logSummary(log, "Commodity sync", result);
    }

    /** Refreshes a single commodity by code, delegating to the snapshot processor. */
    @Override
    public void refresh(String code) {
        snapshotProcessor.refreshOne(code);
    }

    /** Whether a commodity with the given code is currently persisted. */
    public boolean exists(String code) {
        return snapshotProcessor.exists(code);
    }

}
