package com.finance.backend.service;

import com.finance.backend.config.CommodityProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CommodityCandleRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CandlePruner;
import com.finance.backend.util.MarketBatchRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class CommodityUpdateService implements MarketRefresher {

    private static final int BATCH_PARALLELISM = 5;

    private final CommodityCandleRepository commodityCandleRepository;
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService;
    private final CommoditySnapshotProcessor snapshotProcessor;
    private final ExchangeRateProvider exchangeRateProvider;
    private final PreciousMetalDerivativeCalculator derivativeCalculator;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final YahooSymbolResolver yahooSymbolResolver;
    private final TransactionTemplate transactionTemplate;
    private final int yearsToKeep;

    public CommodityUpdateService(CommodityCandleRepository commodityCandleRepository,
                                  MarketCacheService<Commodity, CommodityCandle> commodityCacheService,
                                  CommoditySnapshotProcessor snapshotProcessor,
                                  ExchangeRateProvider exchangeRateProvider,
                                  PreciousMetalDerivativeCalculator derivativeCalculator,
                                  TrackedAssetQueryService trackedAssetQueryService,
                                  YahooSymbolResolver yahooSymbolResolver,
                                  TransactionTemplate transactionTemplate,
                                  CommodityProperties commodityProperties) {
        this.commodityCandleRepository = commodityCandleRepository;
        this.commodityCacheService = commodityCacheService;
        this.snapshotProcessor = snapshotProcessor;
        this.exchangeRateProvider = exchangeRateProvider;
        this.derivativeCalculator = derivativeCalculator;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.yahooSymbolResolver = yahooSymbolResolver;
        this.transactionTemplate = transactionTemplate;
        this.yearsToKeep = commodityProperties.getYearsToKeep();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.COMMODITY;
    }

    @Override
    public void refreshAll() {
        pruneOldCommodityCandles();
        List<String> enabledCodes = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.COMMODITY);
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
                    commodityCacheService.refreshHistory(code);
                },
                code -> code,
                log, "Commodity", "update", BATCH_PARALLELISM);

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

    private void pruneOldCommodityCandles() {
        CandlePruner.pruneByYears(transactionTemplate, yearsToKeep,
                cutoffDate -> commodityCandleRepository.deleteByCandleDateBefore(cutoffDate));
    }
}
