package com.finance.backend.service;

import com.finance.backend.client.YahooForexClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.ForexMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.repository.ForexCandleRepository;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CandleBatchUpsertTemplate;
import com.finance.backend.util.CandlePruner;
import com.finance.backend.util.SyntheticPriceCalculator;
import com.finance.backend.util.YahooRangePolicy;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ForexCandleService implements CandleBatchRefresher {

    private final YahooForexClient yahooForexClient;
    private final ForexMapper forexMapper;
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final TransactionTemplate transactionTemplate;
    private final int yearsToKeep;
    private final int minCandlesForIncremental;
    private final int scale;
    private final ZoneId appZone;

    public ForexCandleService(YahooForexClient yahooForexClient,
                                   ForexMapper forexMapper,
                                   ForexRepository forexRepository,
                                   ForexCandleRepository forexCandleRepository,
                                   MarketCacheService<Forex, ForexCandle> forexCacheService,
                                   PlatformTransactionManager transactionManager,
                                   AppProperties appProperties) {
        this.yahooForexClient = yahooForexClient;
        this.forexMapper = forexMapper;
        this.forexRepository = forexRepository;
        this.forexCandleRepository = forexCandleRepository;
        this.forexCacheService = forexCacheService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.yearsToKeep = appProperties.getForex().getYearsToKeep();
        this.minCandlesForIncremental = appProperties.getForex().getMinCandlesForIncremental();
        this.scale = appProperties.getScale();
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.FOREX;
    }

    @Override
    public void refreshAll() {
        pruneOldForexCandles();
        List<Forex> allForex = forexRepository.findAll();
        log.info("Starting Yahoo forex candle sync for {} pairs", allForex.size());
        Forex usdtry = allForex.stream()
                .filter(f -> "USDTRY".equals(f.getCurrencyCode()))
                .findFirst()
                .orElse(null);
        if (usdtry == null) {
            log.error("USDTRY not found, skipping candle sync");
            return;
        }
        updateForexCandles(usdtry, Map.of());
        forexCacheService.refreshHistory("USDTRY");
        Map<String, YahooCandleDto> usdtryCandleMap = forexCacheService.getHistory("USDTRY")
                .stream()
                .collect(Collectors.toMap(
                        c -> c.getCandleDate().toLocalDate().toString(),
                        this::toYahooCandleDto,
                        (a, b) -> a));

        List<Forex> nonUsdTryForex = allForex.stream()
                .filter(forex -> !"USDTRY".equals(forex.getCurrencyCode()))
                .toList();

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                nonUsdTryForex,
                forex -> {
                    updateForexCandles(forex, usdtryCandleMap);
                    forexCacheService.refreshHistory(forex.getCurrencyCode());
                },
                Forex::getCurrencyCode,
                "candle",
                5,
                (forex, e) -> log.error("Candle sync failed for {}: {}", forex.getCurrencyCode(), e.getMessage(), e),
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("Yahoo CB is OPEN, stopping candle sync. {} success, {} failed so far",
                        stopped.successCount(), stopped.failCount()));

        BatchLogHelper.logSummary(log, "Yahoo candle sync", result);
    }

    private void updateForexCandles(Forex forex, Map<String, YahooCandleDto> usdtryCandleMap) {
        String baseSymbol = forex.getCurrencyCode();
        String yahooSymbol = baseSymbol + "=X";
        long candleCount = forexCandleRepository.countByCurrencyCode(baseSymbol);
        String range = candleCount < minCandlesForIncremental
                ? "5y"
                : forexCandleRepository.findFirstByCurrencyCodeOrderByCandleDateDesc(baseSymbol)
                .map(lastCandle -> YahooRangePolicy.fromLastCandle(lastCandle.getCandleDate(), appZone, "5y"))
                .orElse("5y");
        try {
            List<YahooCandleDto> candles = yahooForexClient.fetchCandles(yahooSymbol, range, "1d", true);
            if (!candles.isEmpty()) {
                int saved = transactionTemplate.execute(status -> saveCandleBatch(forex, candles));
                if (saved > 0 && (!"5y".equals(range) || candleCount + saved >= minCandlesForIncremental)) {
                    return;
                }
                if ("5y".equals(range)) {
                    log.info("{} has only {} candles (need {}), also trying synthetic",
                            baseSymbol, candleCount + saved, minCandlesForIncremental);
                }
            }
        } catch (CallNotPermittedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Direct candle fetch failed for {}, trying synthetic", baseSymbol, e);
        }
        if (!"USDTRY".equals(baseSymbol)) {
            trySyntheticCandles(forex, usdtryCandleMap);
        } else {
            throw new ExternalApiException("Yahoo Finance", "All candle attempts failed for " + baseSymbol);
        }
    }

    private void trySyntheticCandles(Forex forex, Map<String, YahooCandleDto> usdtryCandleMap) {
        if (usdtryCandleMap.isEmpty()) {
            throw new ExternalApiException("Yahoo Finance",
                    "USDTRY candles not available for " + forex.getCurrencyCode());
        }
        String baseCurrency = forex.getCurrencyCode().replace("TRY", "");
        String[] attempts = {baseCurrency + "USD=X", "USD" + baseCurrency + "=X"};
        for (String symbol : attempts) {
            try {
                List<YahooCandleDto> pairCandles = yahooForexClient.fetchCandles(symbol, "5y", "1d", true);
                if (!pairCandles.isEmpty()) {
                    boolean isUsdBase = symbol.startsWith("USD");
                    List<YahooCandleDto> syntheticCandles = SyntheticPriceCalculator.buildSyntheticCandles(
                            pairCandles, usdtryCandleMap, isUsdBase, scale);
                    int saved = transactionTemplate.execute(status -> saveCandleBatch(forex, syntheticCandles));
                    log.debug("Saved {} synthetic candles for {} via {}", saved, forex.getCurrencyCode(), symbol);
                    return;
                }
            } catch (Exception e) {
                log.warn("Synthetic candle {} failed for {}", symbol, forex.getCurrencyCode(), e);
            }
        }
        throw new ExternalApiException("Yahoo Finance",
                "All candle attempts failed for " + forex.getCurrencyCode());
    }

    private YahooCandleDto toYahooCandleDto(ForexCandle candle) {
        return new YahooCandleDto(candle.getCandleDate(), candle.getOpen(), candle.getHigh(),
                candle.getLow(), candle.getClose(), null);
    }

    private int saveCandleBatch(Forex forex, List<YahooCandleDto> candleDtos) {
        List<YahooCandleDto> uniqueDtos = candleDtos.stream()
                .collect(Collectors.toMap(
                        dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS),
                        dto -> dto, (a, b) -> b, LinkedHashMap::new))
                .values().stream().toList();
        CandleBatchUpsertTemplate.UpsertResult<ForexCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                uniqueDtos,
                dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS),
                keys -> forexCandleRepository.findByCurrencyCodeAndCandleDateIn(forex.getCurrencyCode(), keys),
                candle -> candle.getCandleDate().truncatedTo(ChronoUnit.DAYS),
                forexMapper::updateCandleEntity,
                dto -> forexMapper.toCandleEntity(dto, forex.getCurrencyCode(), forex));

        if (!upsertResult.newEntities().isEmpty()) {
            forexCandleRepository.saveAll(upsertResult.newEntities());
        }
        return upsertResult.totalChanged();
    }

    private void pruneOldForexCandles() {
        CandlePruner.pruneByYears(
                transactionTemplate,
                yearsToKeep,
                cutoffDate -> forexCandleRepository.deleteByCandleDateBefore(cutoffDate));
    }
}
