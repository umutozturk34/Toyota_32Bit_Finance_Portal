package com.finance.backend.service;

import com.finance.backend.client.YahooForexClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.config.ForexProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.internal.YahooChartFullResult;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.ForexMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.repository.ForexCandleRepository;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.util.SyntheticPriceCalculator;
import com.finance.backend.util.YahooRangePolicy;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class ForexSnapshotProcessor {

    private static final String USDTRY = "USDTRY";
    private static final String DEFAULT_RANGE = "5y";
    private static final String INTERVAL_DAILY = "1d";

    private final YahooForexClient yahooForexClient;
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final ForexEntityWriter entityWriter;
    private final ForexMapper forexMapper;
    private final TransactionTemplate transactionTemplate;
    private final BigDecimal spreadRate;
    private final int scale;
    private final ZoneId appZone;

    public ForexSnapshotProcessor(YahooForexClient yahooForexClient,
                                  ForexRepository forexRepository,
                                  ForexCandleRepository forexCandleRepository,
                                  MarketCacheService<Forex, ForexCandle> forexCacheService,
                                  ForexEntityWriter entityWriter,
                                  ForexMapper forexMapper,
                                  TransactionTemplate transactionTemplate,
                                  AppProperties appProperties,
                                  ForexProperties forexProperties) {
        this.yahooForexClient = yahooForexClient;
        this.forexRepository = forexRepository;
        this.forexCandleRepository = forexCandleRepository;
        this.forexCacheService = forexCacheService;
        this.entityWriter = entityWriter;
        this.forexMapper = forexMapper;
        this.transactionTemplate = transactionTemplate;
        this.spreadRate = forexProperties.getSpreadRate();
        this.scale = appProperties.getScale();
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    public void updatePair(Forex forex, Map<String, YahooCandleDto> usdtryCandleMap) {
        String baseSymbol = forex.getCurrencyCode();
        String yahooSymbol = baseSymbol + "=X";
        boolean wasEmpty = forexCandleRepository.countByCurrencyCode(baseSymbol) == 0;
        String range = forexCandleRepository.findFirstByCurrencyCodeOrderByCandleDateDesc(baseSymbol)
                .map(lastCandle -> YahooRangePolicy.fromLastCandle(lastCandle.getCandleDate(), appZone, DEFAULT_RANGE))
                .orElse(DEFAULT_RANGE);
        try {
            YahooChartFullResult result = yahooForexClient.fetchChartFull(yahooSymbol, range, INTERVAL_DAILY, true);
            if (hasUsableQuote(result)) {
                int saved = transactionTemplate.execute(status -> {
                    entityWriter.applyDirect(forex, result.quote(), spreadRate, scale);
                    return entityWriter.upsertCandles(forex, result.candles());
                });
                forexCacheService.putSnapshot(baseSymbol, forex);
                if (saved > 0 && (!wasEmpty || USDTRY.equals(baseSymbol))) {
                    return;
                }
                if (saved > 0) {
                    log.info("{} first-time direct returned {} candles, also trying synthetic backfill",
                            baseSymbol, saved);
                }
            }
        } catch (CallNotPermittedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Direct fetch failed for {}, trying synthetic", baseSymbol, e);
        }
        if (USDTRY.equals(baseSymbol)) {
            throw new ExternalApiException("Yahoo Finance", "All forex attempts failed for " + baseSymbol);
        }
        trySyntheticUpdate(forex, usdtryCandleMap);
    }

    public void refreshOne(String code) {
        Forex forex = forexRepository.findById(code).orElse(null);
        if (forex == null) {
            log.warn("Forex pair {} not found for single-code refresh", code);
            return;
        }
        Map<String, YahooCandleDto> usdtryCandleMap = forexCacheService.getHistory(USDTRY).stream()
                .collect(java.util.stream.Collectors.toMap(
                        c -> c.getCandleDate().toLocalDate().toString(),
                        forexMapper::toYahooCandleDto,
                        (a, b) -> a));
        updatePair(forex, usdtryCandleMap);
        forexCacheService.refreshHistory(code);
    }

    public boolean exists(String code) {
        try {
            YahooChartFullResult result = yahooForexClient.fetchChartFull(code + "=X", "1d", INTERVAL_DAILY, true);
            return hasUsableQuote(result);
        } catch (Exception e) {
            log.warn("Forex existence check failed for {}: {}", code, e.getMessage());
            return false;
        }
    }

    private void trySyntheticUpdate(Forex forex, Map<String, YahooCandleDto> usdtryCandleMap) {
        if (usdtryCandleMap.isEmpty()) {
            throw new ExternalApiException("Yahoo Finance",
                    "USDTRY candles not available for " + forex.getCurrencyCode());
        }
        Forex usdtry = forexRepository.findById(USDTRY).orElse(null);
        if (usdtry == null || usdtry.getCurrentPrice() == null) {
            throw new ExternalApiException("Yahoo Finance",
                    "USDTRY snapshot not available for synthetic of " + forex.getCurrencyCode());
        }
        String baseCurrency = forex.getCurrencyCode().replace("TRY", "");
        String[] attempts = {baseCurrency + "USD=X", "USD" + baseCurrency + "=X"};
        for (String symbol : attempts) {
            try {
                YahooChartFullResult result = yahooForexClient.fetchChartFull(symbol, DEFAULT_RANGE, INTERVAL_DAILY, true);
                if (!hasUsableQuote(result) || result.candles().isEmpty()) continue;
                boolean isUsdBase = symbol.startsWith("USD");
                int saved = transactionTemplate.execute(status -> {
                    entityWriter.applySynthetic(forex, result.quote(), usdtry, isUsdBase, spreadRate, scale);
                    List<YahooCandleDto> syntheticCandles = SyntheticPriceCalculator.buildSyntheticCandles(
                            result.candles(), usdtryCandleMap, isUsdBase, scale);
                    return entityWriter.upsertCandles(forex, syntheticCandles);
                });
                forexCacheService.putSnapshot(forex.getCurrencyCode(), forex);
                log.debug("Saved {} synthetic candles for {} via {}", saved, forex.getCurrencyCode(), symbol);
                return;
            } catch (Exception e) {
                log.warn("Synthetic {} failed for {}", symbol, forex.getCurrencyCode(), e);
            }
        }
        throw new ExternalApiException("Yahoo Finance",
                "All synthetic attempts failed for " + forex.getCurrencyCode());
    }

    private boolean hasUsableQuote(YahooChartFullResult result) {
        return result.quote() != null && result.quote().regularMarketPrice() != null;
    }
}
