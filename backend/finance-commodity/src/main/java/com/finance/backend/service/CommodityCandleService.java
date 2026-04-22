package com.finance.backend.service;

import com.finance.backend.client.YahooCommodityClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.CommodityMapper;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CommodityCandleRepository;
import com.finance.backend.repository.CommodityRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CandleBatchUpsertTemplate;
import com.finance.backend.util.CandlePruner;
import com.finance.backend.util.SyntheticPriceCalculator;
import com.finance.backend.util.YahooRangePolicy;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Log4j2
public class CommodityCandleService implements CandleBatchRefresher {

    private final YahooCommodityClient yahooCommodityClient;
    private final CommodityMapper commodityMapper;
    private final CommodityRepository commodityRepository;
    private final CommodityCandleRepository commodityCandleRepository;
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService;
    private final ExchangeRateProvider exchangeRateProvider;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final PreciousMetalDerivativeCalculator derivativeCalculator;
    private final YahooSymbolResolver yahooSymbolResolver;
    private final TransactionTemplate transactionTemplate;
    private final int yearsToKeep;
    private final int scale;
    private final ZoneId appZone;

    public CommodityCandleService(YahooCommodityClient yahooCommodityClient,
                                  CommodityMapper commodityMapper,
                                  CommodityRepository commodityRepository,
                                  CommodityCandleRepository commodityCandleRepository,
                                  MarketCacheService<Commodity, CommodityCandle> commodityCacheService,
                                  ExchangeRateProvider exchangeRateProvider,
                                  TrackedAssetQueryService trackedAssetQueryService,
                                  PreciousMetalDerivativeCalculator derivativeCalculator,
                                  YahooSymbolResolver yahooSymbolResolver,
                                  TransactionTemplate transactionTemplate,
                                  AppProperties appProperties) {
        this.yahooCommodityClient = yahooCommodityClient;
        this.commodityMapper = commodityMapper;
        this.commodityRepository = commodityRepository;
        this.commodityCandleRepository = commodityCandleRepository;
        this.commodityCacheService = commodityCacheService;
        this.exchangeRateProvider = exchangeRateProvider;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.derivativeCalculator = derivativeCalculator;
        this.yahooSymbolResolver = yahooSymbolResolver;
        this.transactionTemplate = transactionTemplate;
        this.yearsToKeep = appProperties.getCommodity().getYearsToKeep();
        this.scale = appProperties.getScale();
        this.appZone = ZoneId.of(appProperties.getTimezone());
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
            log.info("No Yahoo-fetchable commodities enabled, skipping candle sync");
            return;
        }

        Map<String, YahooCandleDto> usdtryCandleMap = loadUsdTryCandleMap();
        if (usdtryCandleMap.isEmpty()) {
            log.error("USDTRY candles unavailable, skipping commodity candle sync");
            return;
        }

        log.info("Starting commodity candle sync for {} items", fetchableCodes.size());

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                fetchableCodes,
                code -> updateCommodityCandles(code, usdtryCandleMap),
                code -> code,
                "candle",
                5,
                (code, e) -> log.error("Candle sync failed for {}: {}", code, e.getMessage(), e),
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("Yahoo CB is OPEN, stopping commodity candle sync. {} success, {} failed so far",
                        stopped.successCount(), stopped.failCount()));

        BatchLogHelper.logSummary(log, "Commodity candle sync", result);
        derivativeCalculator.refreshDerivativeCandles();
    }

    public void refreshTrackedCommodityCandles(String code) {
        String normalized = yahooSymbolResolver.normalize(code);
        if (yahooSymbolResolver.resolve(normalized) == null) return;
        Map<String, YahooCandleDto> usdtryCandleMap = loadUsdTryCandleMap();
        if (usdtryCandleMap.isEmpty()) {
            log.error("USDTRY candles unavailable, skipping single refresh for {}", normalized);
            return;
        }
        updateCommodityCandles(normalized, usdtryCandleMap);
        log.info("Refreshed tracked commodity candles for {}", normalized);
    }

    private Map<String, YahooCandleDto> loadUsdTryCandleMap() {
        return exchangeRateProvider.getUsdTryHistory();
    }

    private void updateCommodityCandles(String commodityCode, Map<String, YahooCandleDto> usdtryCandleMap) {
        String yahooSymbol = yahooSymbolResolver.resolve(commodityCode);
        if (yahooSymbol == null) return;
        String range = commodityCandleRepository.findFirstByCommodityCodeOrderByCandleDateDesc(commodityCode)
                .map(last -> YahooRangePolicy.fromLastCandle(last.getCandleDate(), appZone, "5y"))
                .orElse("5y");

        List<YahooCandleDto> usdCandles = yahooCommodityClient.fetchCandles(yahooSymbol, range, "1d", true);
        if (usdCandles.isEmpty()) {
            throw new ExternalApiException("Yahoo Finance", "No candles returned for " + yahooSymbol);
        }

        List<YahooCandleDto> tryCandles = SyntheticPriceCalculator.buildSyntheticCandles(
                usdCandles, usdtryCandleMap, false, scale);
        if (tryCandles.isEmpty()) {
            log.warn("No USDTRY-aligned candles for {} (usd={}, usdtry={} entries)",
                    commodityCode, usdCandles.size(), usdtryCandleMap.size());
            return;
        }

        Commodity commodity = commodityRepository.findById(commodityCode)
                .orElseGet(() -> Commodity.builder()
                        .commodityCode(commodityCode)
                        .yahooSymbol(yahooSymbol)
                        .build());
        transactionTemplate.executeWithoutResult(status -> saveCandleBatch(commodity, tryCandles));
        commodityCacheService.refreshHistory(commodityCode);
    }

    private void saveCandleBatch(Commodity commodity, List<YahooCandleDto> candleDtos) {
        if (commodity.getCommodityCode() == null || commodity.getCommodityCode().isBlank()) {
            return;
        }
        if (commodityRepository.findById(commodity.getCommodityCode()).isEmpty()) {
            commodityRepository.save(commodity);
        }
        List<YahooCandleDto> uniqueDtos = candleDtos.stream()
                .collect(Collectors.toMap(
                        dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS),
                        dto -> dto, (a, b) -> b, LinkedHashMap::new))
                .values().stream().toList();

        CandleBatchUpsertTemplate.UpsertResult<CommodityCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                uniqueDtos,
                dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS),
                keys -> commodityCandleRepository.findByCommodityCodeAndCandleDateIn(commodity.getCommodityCode(), keys),
                candle -> candle.getCandleDate().truncatedTo(ChronoUnit.DAYS),
                (existing, dto) -> {
                    commodityMapper.updateCandleEntity(existing, dto);
                    normalize(existing);
                },
                dto -> {
                    CommodityCandle candle = commodityMapper.toCandleEntity(dto, commodity.getCommodityCode(), commodity);
                    normalize(candle);
                    return candle;
                });

        if (!upsertResult.newEntities().isEmpty()) {
            commodityCandleRepository.saveAll(upsertResult.newEntities());
        }
    }

    private void normalize(CommodityCandle candle) {
        candle.scaleAndNormalizeOhlc(scale);
        LocalDateTime candleDate = candle.getCandleDate();
        if (candleDate != null) {
            candle.setCandleDate(candleDate.toLocalDate().atStartOfDay());
        }
    }

    private void pruneOldCommodityCandles() {
        CandlePruner.pruneByYears(
                transactionTemplate,
                yearsToKeep,
                cutoffDate -> commodityCandleRepository.deleteByCandleDateBefore(cutoffDate));
    }
}
