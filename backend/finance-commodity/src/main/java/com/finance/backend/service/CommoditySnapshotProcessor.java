package com.finance.backend.service;

import com.finance.backend.client.YahooCommodityClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.config.CommodityProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.dto.internal.YahooChartFullResult;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.CommodityMapper;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.CommoditySnapshotInput;
import com.finance.backend.repository.CommodityCandleRepository;
import com.finance.backend.repository.CommodityRepository;
import com.finance.backend.util.SyntheticPriceCalculator;
import com.finance.backend.util.TrackedRefreshRunner;
import com.finance.backend.util.YahooRangePolicy;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

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
        this.scale = appProperties.getScale();
        this.appZone = ZoneId.of(appProperties.getTimezone());
        this.chartRange = commodityProperties.getChartRange();
        this.chartInterval = commodityProperties.getChartInterval();
    }

    public void updateOne(String commodityCode, Map<String, YahooCandleDto> usdtryCandleMap,
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

        List<YahooCandleDto> tryCandles = SyntheticPriceCalculator.buildSyntheticCandles(
                result.candles(), usdtryCandleMap, false, scale);
        if (tryCandles.isEmpty()) {
            log.warn("No USDTRY-aligned candles for {} (usd={}, usdtry={} entries), skipping",
                    commodityCode, result.candles().size(), usdtryCandleMap.size());
            return;
        }
        YahooCandleDto todayTryCandle = tryCandles.get(tryCandles.size() - 1);
        YahooCandleDto previousTryCandle = tryCandles.size() >= 2 ? tryCandles.get(tryCandles.size() - 2) : null;
        CommoditySnapshotInput snapshot = commodityMapper.toSnapshotInput(quote, todayTryCandle, previousTryCandle);

        transactionTemplate.executeWithoutResult(status -> {
            entityWriter.applySnapshot(commodity, snapshot, yahooSymbol, scale);
            entityWriter.upsertCandles(commodity, tryCandles, scale);
        });
        commodityCacheService.putSnapshot(commodityCode, commodity);
        if (derivativeCalculator.hasDerivatives(commodityCode)) {
            derivativeCalculator.refreshDerivatives(commodity, usdTry.currentRate(), usdTry.previousRate());
        }
    }

    public void refreshOne(String code) {
        TrackedRefreshRunner.refreshSnapshot(code, yahooSymbolResolver::normalize, normalized -> {
            if (yahooSymbolResolver.resolve(normalized) == null) return false;
            Map<String, YahooCandleDto> usdtryMap = exchangeRateProvider.getUsdTryHistory();
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
