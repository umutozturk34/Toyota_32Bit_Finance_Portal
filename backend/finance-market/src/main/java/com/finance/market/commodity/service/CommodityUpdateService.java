package com.finance.market.commodity.service;
import com.finance.market.commodity.model.Commodity;

import com.finance.market.core.service.ExchangeRateProvider;

import com.finance.market.core.service.ExchangeRateSnapshot;

import com.finance.market.core.service.MarketRefresher;

import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.market.commodity.config.CommodityProperties;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.util.BatchLogHelper;
import com.finance.common.util.BatchUpdateRunner;
import com.finance.market.core.util.MarketBatchRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class CommodityUpdateService implements MarketRefresher {

    private final CommoditySnapshotProcessor snapshotProcessor;
    private final ExchangeRateProvider exchangeRateProvider;
    private final PreciousMetalDerivativeCalculator derivativeCalculator;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final YahooSymbolResolver yahooSymbolResolver;
    private final int batchMinSample;

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

    @Override
    public MarketType getMarketType() {
        return MarketType.COMMODITY;
    }

    @Override
    public void refreshAll() {
        List<String> enabledCodes = trackedAssetQueryService.getCodes(TrackedAssetType.COMMODITY);
        List<String> fetchableCodes = enabledCodes.stream()
                .filter(code -> yahooSymbolResolver.resolve(code) != null)
                .toList();
        if (fetchableCodes.isEmpty()) {
            log.info("No Yahoo-fetchable commodities enabled, skipping sync");
            return;
        }

        Map<String, YahooCandleDto> usdtryCandleMap = exchangeRateProvider.getUsdTryHistory();
        if (usdtryCandleMap.isEmpty()) {
            log.error("USDTRY candles unavailable, skipping commodity sync");
            return;
        }
        ExchangeRateSnapshot usdTry = exchangeRateProvider.getCurrentUsdTry();

        log.info("Starting commodity sync for {} items", fetchableCodes.size());

        BatchUpdateRunner.Result result = MarketBatchRunner.run(
                fetchableCodes,
                code -> {
                    snapshotProcessor.updateOne(code, usdtryCandleMap, usdTry);
                },
                code -> code,
                log, "Commodity", "update", batchMinSample);

        BatchLogHelper.logSummary(log, "Commodity sync", result);
        derivativeCalculator.refreshDerivativeCandles();
    }

    @Override
    public void refresh(String code) {
        snapshotProcessor.refreshOne(code);
    }

    public boolean exists(String code) {
        return snapshotProcessor.exists(code);
    }

}
