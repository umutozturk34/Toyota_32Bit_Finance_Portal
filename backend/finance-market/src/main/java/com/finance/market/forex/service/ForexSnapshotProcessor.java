package com.finance.market.forex.service;
import com.finance.cache.service.MarketCacheService;

import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.market.forex.client.YahooForexClient;
import com.finance.common.config.AppProperties;
import com.finance.market.forex.config.ForexProperties;
import com.finance.common.dto.external.YahooCandleDto;
import com.finance.common.dto.external.YahooQuoteDto;
import com.finance.common.dto.internal.YahooChartFullResult;
import com.finance.common.exception.ExternalApiException;
import com.finance.market.forex.mapper.ForexMapper;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.market.forex.repository.ForexRepository;
import com.finance.common.util.SyntheticPriceCalculator;
import com.finance.common.util.YahooRangePolicy;
import com.finance.common.util.YahooSymbolSuffix;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Component
public class ForexSnapshotProcessor implements MarketSnapshotProcessor {

    private final YahooForexClient yahooForexClient;
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final MarketCacheService<Forex> forexCacheService;
    private final ForexEntityWriter entityWriter;
    private final ForexMapper forexMapper;
    private final TransactionTemplate transactionTemplate;
    private final BigDecimal spreadRate;
    private final int scale;
    private final ZoneId appZone;
    private final String baseCurrency;
    private final String chartRange;
    private final String chartInterval;

    public ForexSnapshotProcessor(YahooForexClient yahooForexClient,
                                  ForexRepository forexRepository,
                                  ForexCandleRepository forexCandleRepository,
                                  MarketCacheService<Forex> forexCacheService,
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
        this.baseCurrency = forexProperties.getBaseCurrency();
        this.chartRange = forexProperties.getChartRange();
        this.chartInterval = forexProperties.getChartInterval();
    }

    public void updatePair(Forex forex, Map<String, YahooCandleDto> usdtryCandleMap) {
        String baseSymbol = forex.getCurrencyCode();
        String yahooSymbol = baseSymbol + YahooSymbolSuffix.FOREX;
        boolean wasEmpty = forexCandleRepository.countByCurrencyCode(baseSymbol) == 0;
        String range = forexCandleRepository.findFirstByCurrencyCodeOrderByCandleDateDesc(baseSymbol)
                .map(lastCandle -> YahooRangePolicy.fromLastCandle(lastCandle.getCandleDate(), appZone, chartRange))
                .orElse(chartRange);
        try {
            YahooChartFullResult<YahooQuoteDto> result = yahooForexClient.fetchChartFull(yahooSymbol, range, chartInterval, true);
            if (hasUsableQuote(result)) {
                int saved = transactionTemplate.execute(status -> {
                    entityWriter.applyDirect(forex, result.quote(), spreadRate, scale);
                    int upserted = entityWriter.upsertCandles(forex, result.candles());
                    entityWriter.refreshChangePercentFromCandles(forex, scale);
                    return upserted;
                });
                forexCacheService.putSnapshot(baseSymbol, forex);
                if (saved > 0 && (!wasEmpty || baseCurrency.equals(baseSymbol))) {
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
        if (baseCurrency.equals(baseSymbol)) {
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
        Map<String, YahooCandleDto> usdtryCandleMap = forexCandleRepository
                .findByCurrencyCodeOrderByCandleDateAsc(baseCurrency).stream()
                .collect(Collectors.toMap(
                        c -> c.getCandleDate().toLocalDate().toString(),
                        forexMapper::toYahooCandleDto,
                        (a, b) -> a));
        updatePair(forex, usdtryCandleMap);
    }

    public boolean exists(String code) {
        try {
            YahooChartFullResult<YahooQuoteDto> result = yahooForexClient.fetchChartFull(code + YahooSymbolSuffix.FOREX, "1d", chartInterval, true);
            return hasUsableQuote(result);
        } catch (Exception e) {
            log.warn("Forex existence check failed for {}: {}", code, e.getMessage());
            return false;
        }
    }

    private void trySyntheticUpdate(Forex forex, Map<String, YahooCandleDto> usdtryCandleMap) {
        if (usdtryCandleMap.isEmpty()) {
            throw new ExternalApiException("Yahoo Finance",
                    baseCurrency + " candles not available for " + forex.getCurrencyCode());
        }
        Forex usdtry = forexRepository.findById(baseCurrency).orElse(null);
        if (usdtry == null || usdtry.getCurrentPrice() == null) {
            throw new ExternalApiException("Yahoo Finance",
                    baseCurrency + " snapshot not available for synthetic of " + forex.getCurrencyCode());
        }
        String coinPart = forex.getCurrencyCode().replace("TRY", "");
        String[] attempts = {coinPart + "USD" + YahooSymbolSuffix.FOREX, "USD" + coinPart + YahooSymbolSuffix.FOREX};
        for (String symbol : attempts) {
            try {
                YahooChartFullResult<YahooQuoteDto> result = yahooForexClient.fetchChartFull(symbol, chartRange, chartInterval, true);
                if (!hasUsableQuote(result) || result.candles().isEmpty()) continue;
                boolean isUsdBase = symbol.startsWith("USD");
                List<YahooCandleDto> syntheticCandles = SyntheticPriceCalculator.buildSyntheticCandles(
                        result.candles(), usdtryCandleMap, isUsdBase, scale);
                if (syntheticCandles.isEmpty()) continue;
                YahooCandleDto todayTryCandle = syntheticCandles.get(syntheticCandles.size() - 1);
                int saved = transactionTemplate.execute(status -> {
                    entityWriter.applySynthetic(forex, result.quote(), usdtry, isUsdBase,
                            spreadRate, scale, todayTryCandle);
                    int upserted = entityWriter.upsertCandles(forex, syntheticCandles);
                    entityWriter.refreshChangePercentFromCandles(forex, scale);
                    return upserted;
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

    private boolean hasUsableQuote(YahooChartFullResult<YahooQuoteDto> result) {
        return result.quote() != null && result.quote().regularMarketPrice() != null;
    }
}
