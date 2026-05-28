package com.finance.market.commodity.service;
import com.finance.market.core.service.ExchangeRateProvider;

import com.finance.market.core.service.ExchangeRateSnapshot;

import com.finance.market.core.service.MarketSnapshotProcessor;

import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.common.model.TrackedAssetType;

import com.finance.market.core.cache.MarketCacheService;


import com.finance.market.commodity.client.YahooCommodityClient;
import com.finance.common.config.AppProperties;
import com.finance.market.commodity.config.CommodityProperties;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.dto.external.YahooQuoteDto;
import com.finance.market.core.dto.internal.YahooChartFullResult;
import com.finance.common.exception.ExternalApiException;
import com.finance.market.commodity.mapper.CommodityMapper;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.model.CommoditySnapshotInput;
import com.finance.market.commodity.repository.CommodityCandleRepository;
import com.finance.market.commodity.repository.CommodityRepository;
import com.finance.market.core.util.PriceCrossCalculator;
import com.finance.market.core.util.TrackedRefreshRunner;
import com.finance.market.core.util.YahooRangePolicy;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Fetches a commodity's USD chart from Yahoo and converts every candle to TRY at its own date's
 * USD/TRY rate ({@link PriceCrossCalculator}); commodities are stored in TRY. Persists the snapshot
 * and candles, recomputes change, and regenerates any precious-metal derivatives. Only fetches the
 * tail since the last stored candle.
 */
@Log4j2
@Component
public class CommoditySnapshotProcessor implements MarketSnapshotProcessor {

    private final YahooCommodityClient yahooCommodityClient;
    private final CommodityMapper commodityMapper;
    private final CommodityRepository commodityRepository;
    private final CommodityCandleRepository commodityCandleRepository;
    private final MarketCacheService<Commodity> commodityCacheService;
    private final ExchangeRateProvider exchangeRateProvider;
    private final PreciousMetalDerivativeCalculator derivativeCalculator;
    private final YahooSymbolResolver yahooSymbolResolver;
    private final CommoditySegmentResolver segmentResolver;
    private final CommodityEntityWriter entityWriter;
    private final TransactionTemplate transactionTemplate;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final int scale;
    private final ZoneId appZone;
    private final String chartRange;
    private final String chartInterval;

    public CommoditySnapshotProcessor(YahooCommodityClient yahooCommodityClient,
                                      CommodityMapper commodityMapper,
                                      CommodityRepository commodityRepository,
                                      CommodityCandleRepository commodityCandleRepository,
                                      MarketCacheService<Commodity> commodityCacheService,
                                      ExchangeRateProvider exchangeRateProvider,
                                      PreciousMetalDerivativeCalculator derivativeCalculator,
                                      YahooSymbolResolver yahooSymbolResolver,
                                      CommoditySegmentResolver segmentResolver,
                                      CommodityEntityWriter entityWriter,
                                      TransactionTemplate transactionTemplate,
                                      TrackedAssetQueryService trackedAssetQueryService,
                                      AppProperties appProperties,
                                      CommodityProperties commodityProperties) {
        this.yahooCommodityClient = yahooCommodityClient;
        this.commodityMapper = commodityMapper;
        this.commodityRepository = commodityRepository;
        this.commodityCandleRepository = commodityCandleRepository;
        this.commodityCacheService = commodityCacheService;
        this.exchangeRateProvider = exchangeRateProvider;
        this.derivativeCalculator = derivativeCalculator;
        this.yahooSymbolResolver = yahooSymbolResolver;
        this.segmentResolver = segmentResolver;
        this.entityWriter = entityWriter;
        this.transactionTemplate = transactionTemplate;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.scale = appProperties.getScale();
        this.appZone = ZoneId.of(appProperties.getTimezone());
        this.chartRange = commodityProperties.getChartRange();
        this.chartInterval = commodityProperties.getChartInterval();
    }

    /**
     * Refreshes one commodity: TRY-converts the fetched USD candles, persists snapshot/candles, and
     * cascades to derivatives. Throws when the price, USD/TRY rate, or aligned candles are missing.
     */
    public void updateOne(String commodityCode, Map<String, java.math.BigDecimal> usdtryRateMap,
                          ExchangeRateSnapshot usdTry) {
        String yahooSymbol = yahooSymbolResolver.resolve(commodityCode);
        if (yahooSymbol == null) return;
        String range = commodityCandleRepository.findFirstByCommodityCodeOrderByCandleDateDesc(commodityCode)
                .map(last -> YahooRangePolicy.fromLastCandle(last.getCandleDate(), appZone, chartRange))
                .orElse(chartRange);

        YahooChartFullResult<YahooQuoteDto> result = yahooCommodityClient.fetchChartFull(yahooSymbol, range, chartInterval, true);
        YahooQuoteDto quote = result.quote();
        if (quote == null || quote.regularMarketPrice() == null) {
            throw new ExternalApiException("Yahoo Finance", "No price for " + yahooSymbol);
        }
        if (!usdTry.isAvailable()) {
            throw new ExternalApiException("Yahoo Finance",
                    "USDTRY rate not available for commodity TRY conversion");
        }
        if (result.candles().isEmpty()) {
            throw new ExternalApiException("Yahoo Finance", "No candles returned for " + yahooSymbol);
        }

        Commodity commodity = commodityRepository.findById(commodityCode)
                .orElseGet(() -> Commodity.builder()
                        .commodityCode(commodityCode)
                        .yahooSymbol(yahooSymbol)
                        .commoditySegment(segmentResolver.resolve(commodityCode))
                        .build());

        if (commodity.getName() == null || commodity.getName().isBlank()) {
            String displayName = trackedAssetQueryService
                    .getDisplayNameMap(TrackedAssetType.COMMODITY)
                    .get(commodityCode);
            if (displayName != null && !displayName.isBlank()) {
                commodity.setName(displayName);
            }
        }

        List<YahooCandleDto> tryCandles = PriceCrossCalculator.buildTryCandles(
                result.candles(), usdtryRateMap, scale);
        if (tryCandles.isEmpty()) {
            log.warn("No USDTRY-aligned candles for {} (usd={}, usdtry={} entries)",
                    commodityCode, result.candles().size(), usdtryRateMap.size());
            throw new ExternalApiException("Yahoo Finance",
                    "No USDTRY-aligned candles for " + commodityCode);
        }
        YahooCandleDto todayTryCandle = tryCandles.get(tryCandles.size() - 1);
        YahooCandleDto previousTryCandle = tryCandles.size() >= 2 ? tryCandles.get(tryCandles.size() - 2) : null;
        CommoditySnapshotInput snapshot = commodityMapper.toSnapshotInput(quote, todayTryCandle, previousTryCandle);

        transactionTemplate.executeWithoutResult(status -> {
            entityWriter.applySnapshot(commodity, snapshot, yahooSymbol, scale);
            entityWriter.upsertCandles(commodity, tryCandles, scale);
            entityWriter.refreshChangePercentFromCandles(commodity, scale);
        });
        commodityCacheService.putSnapshot(commodityCode, commodity);
        if (derivativeCalculator.hasDerivatives(commodityCode)) {
            derivativeCalculator.refreshDerivatives(commodity, usdTry.currentRate(), usdTry.previousRate());
            derivativeCalculator.refreshDerivativeCandlesForSource(commodityCode);
        }
    }

    public void refreshOne(String code) {
        TrackedRefreshRunner.refreshSnapshot(code, yahooSymbolResolver::normalize, normalized -> {
            if (yahooSymbolResolver.resolve(normalized) == null) return false;
            Map<String, java.math.BigDecimal> usdtryMap = exchangeRateProvider.getUsdTryHistory();
            ExchangeRateSnapshot usdTry = exchangeRateProvider.getCurrentUsdTry();
            updateOne(normalized, usdtryMap, usdTry);
            return true;
        }, log, "commodity");
    }

    public boolean exists(String code) {
        String normalized = yahooSymbolResolver.normalize(code);
        String yahooSymbol = yahooSymbolResolver.resolve(normalized);
        if (yahooSymbol == null) return false;
        try {
            YahooChartFullResult<YahooQuoteDto> result = yahooCommodityClient.fetchChartFull(yahooSymbol, "1d", chartInterval, true);
            return result.quote() != null && result.quote().regularMarketPrice() != null;
        } catch (Exception e) {
            log.warn("Commodity existence check failed for {}: {}", normalized, e.getMessage());
            return false;
        }
    }
}
