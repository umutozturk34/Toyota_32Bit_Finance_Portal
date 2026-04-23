package com.finance.backend.service;

import com.finance.backend.client.YahooCommodityClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.CommodityMapper;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.CommoditySnapshotInput;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CommodityRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.MarketBatchRunner;
import com.finance.backend.util.TrackedRefreshRunner;
import lombok.extern.log4j.Log4j2;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

@Service
@Log4j2
public class CommoditySnapshotService implements SnapshotBatchRefresher {

    private final YahooCommodityClient yahooCommodityClient;
    private final CommodityMapper commodityMapper;
    private final CommodityRepository commodityRepository;
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService;
    private final ExchangeRateProvider exchangeRateProvider;
    private final PreciousMetalDerivativeCalculator derivativeCalculator;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final YahooSymbolResolver yahooSymbolResolver;
    private final CommoditySegmentResolver segmentResolver;
    private final TransactionTemplate transactionTemplate;
    private final int scale;

    public CommoditySnapshotService(YahooCommodityClient yahooCommodityClient,
                                    CommodityMapper commodityMapper,
                                    CommodityRepository commodityRepository,
                                    MarketCacheService<Commodity, CommodityCandle> commodityCacheService,
                                    ExchangeRateProvider exchangeRateProvider,
                                    PreciousMetalDerivativeCalculator derivativeCalculator,
                                    TrackedAssetQueryService trackedAssetQueryService,
                                    YahooSymbolResolver yahooSymbolResolver,
                                    CommoditySegmentResolver segmentResolver,
                                    TransactionTemplate transactionTemplate,
                                    AppProperties appProperties) {
        this.yahooCommodityClient = yahooCommodityClient;
        this.commodityMapper = commodityMapper;
        this.commodityRepository = commodityRepository;
        this.commodityCacheService = commodityCacheService;
        this.exchangeRateProvider = exchangeRateProvider;
        this.derivativeCalculator = derivativeCalculator;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.yahooSymbolResolver = yahooSymbolResolver;
        this.segmentResolver = segmentResolver;
        this.transactionTemplate = transactionTemplate;
        this.scale = appProperties.getScale();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.COMMODITY;
    }

    public boolean existsInApi(String code) {
        String normalized = yahooSymbolResolver.normalize(code);
        String yahooSymbol = yahooSymbolResolver.resolve(normalized);
        if (yahooSymbol == null) return false;
        try {
            YahooQuoteDto quote = yahooCommodityClient.fetchQuote(yahooSymbol);
            return quote != null && quote.regularMarketPrice() != null;
        } catch (Exception e) {
            log.warn("Commodity existence check failed for {}: {}", normalized, e.getMessage());
            return false;
        }
    }

    public void refreshTrackedCommoditySnapshot(String code) {
        TrackedRefreshRunner.refreshSnapshot(code, yahooSymbolResolver::normalize, normalized -> {
            if (yahooSymbolResolver.resolve(normalized) == null) return false;
            updateCommoditySnapshot(normalized);
            return true;
        }, log, "commodity");
    }

    @Override
    public void refreshAll() {
        List<String> enabledCodes = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.COMMODITY);
        List<String> fetchableCodes = enabledCodes.stream()
                .filter(code -> yahooSymbolResolver.resolve(code) != null)
                .toList();
        if (fetchableCodes.isEmpty()) {
            log.info("No Yahoo-fetchable commodities enabled, skipping snapshot sync");
            return;
        }

        log.info("Starting commodity snapshot sync for {} items", fetchableCodes.size());

        BatchUpdateRunner.Result result = MarketBatchRunner.run(
                fetchableCodes,
                this::updateCommoditySnapshot,
                code -> code,
                log, "Commodity", "snapshot", 5);

        BatchLogHelper.logSummary(log, "Commodity snapshot sync", result);
    }

    private void updateCommoditySnapshot(String commodityCode) {
        String yahooSymbol = yahooSymbolResolver.resolve(commodityCode);
        if (yahooSymbol == null) return;
        YahooQuoteDto quote = yahooCommodityClient.fetchQuote(yahooSymbol);
        if (quote == null || quote.regularMarketPrice() == null) {
            throw new ExternalApiException("Yahoo Finance",
                    "No price for " + yahooSymbol);
        }
        ExchangeRateSnapshot usdTry = exchangeRateProvider.getCurrentUsdTry();
        if (!usdTry.isAvailable()) {
            throw new ExternalApiException("Yahoo Finance",
                    "USDTRY rate not available for commodity TRY conversion");
        }
        CommoditySnapshotInput snapshot = commodityMapper.toSnapshotInput(quote, usdTry.currentRate(), scale);
        Commodity commodity = commodityRepository.findById(commodityCode)
                .orElseGet(() -> Commodity.builder()
                        .commodityCode(commodityCode)
                        .yahooSymbol(yahooSymbol)
                        .commoditySegment(segmentResolver.resolve(commodityCode))
                        .build());
        transactionTemplate.executeWithoutResult(status -> {
            commodity.applyPriceSnapshot(snapshot, scale);
            if (commodity.getYahooSymbol() == null) {
                commodity.setYahooSymbol(yahooSymbol);
            }
            commodityRepository.save(commodity);
        });
        commodityCacheService.putSnapshot(commodityCode, commodity);
        if (derivativeCalculator.hasDerivatives(commodityCode)) {
            derivativeCalculator.refreshDerivatives(commodity, usdTry.currentRate(), usdTry.previousRate());
        }
    }
}
