package com.finance.backend.service;
import com.finance.backend.client.YahooForexClient;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.util.BatchFailureGuard;
import com.finance.backend.mapper.ForexMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.repository.ForexCandleRepository;
import com.finance.backend.repository.ForexRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
@Service
@Log4j2
public class YahooForexService {
    private static final int YEARS_TO_KEEP = 5;
    private static final int MIN_CANDLES_FOR_INCREMENTAL = 1200;
    private final YahooForexClient yahooForexClient;
    private final ForexMapper forexMapper;
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final YahooForexService self;
    public YahooForexService(YahooForexClient yahooForexClient,
                             ForexMapper forexMapper,
                             ForexRepository forexRepository,
                             ForexCandleRepository forexCandleRepository,
                             MarketCacheService<Forex, ForexCandle> forexCacheService,
                             @Lazy YahooForexService self) {
        this.yahooForexClient = yahooForexClient;
        this.forexMapper = forexMapper;
        this.forexRepository = forexRepository;
        this.forexCandleRepository = forexCandleRepository;
        this.forexCacheService = forexCacheService;
        this.self = self;
    }
    public void syncAllYahooSnapshots() {
        List<Forex> allForex = forexRepository.findAll();
        int successCount = 0;
        int failCount = 0;
        List<String> failedCodes = new ArrayList<>();
        for (Forex forex : allForex) {
            try {
                self.updateForexSnapshot(forex);
                successCount++;
                Thread.sleep(2000); 
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failCount++;
                failedCodes.add(forex.getCurrencyCode());
                log.error("Snapshot failed for {}: {}", forex.getCurrencyCode(), e.getMessage(), e);
                BatchFailureGuard.check(successCount, failCount, failedCodes, "snapshot");
            }
        }
        log.info("Yahoo snapshot sync: {} success, {} failed", successCount, failCount);
        if (!failedCodes.isEmpty()) {
            log.warn("Failed currencies: {}", failedCodes);
        }
    }
    public void syncAllYahooCandles() {
        self.pruneOldForexCandles();
        List<Forex> allForex = forexRepository.findAll();
        Forex usdtry = allForex.stream()
                .filter(f -> "USDTRY".equals(f.getCurrencyCode()))
                .findFirst()
                .orElse(null);
        if (usdtry == null) {
            log.error("USDTRY not found, skipping candle sync");
            return;
        }
        self.updateForexCandles(usdtry, Map.of());
        forexCacheService.refreshHistory("USDTRY");
        Map<String, ForexCandle> usdtryCandleMap = forexCacheService.getHistory("USDTRY")
                .stream()
                .collect(Collectors.toMap(
                        c -> c.getCandleDate().toLocalDate().toString(),
                        c -> c,
                        (a, b) -> a));
        int successCount = 0;
        int failCount = 0;
        List<String> failedCodes = new ArrayList<>();
        for (Forex forex : allForex) {
            if ("USDTRY".equals(forex.getCurrencyCode())) {
                continue;
            }
            try {
                self.updateForexCandles(forex, usdtryCandleMap);
                forexCacheService.refreshHistory(forex.getCurrencyCode());
                successCount++;
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failCount++;
                failedCodes.add(forex.getCurrencyCode());
                log.error("Candle sync failed for {}: {}", forex.getCurrencyCode(), e.getMessage(), e);
                BatchFailureGuard.check(successCount, failCount, failedCodes, "candle");
            }
        }
        log.info("Yahoo candle sync: {} success, {} failed", successCount, failCount);
        if (!failedCodes.isEmpty()) {
            log.warn("Failed currencies: {}", failedCodes);
        }
    }
    @Transactional
    public void updateForexSnapshot(Forex forex) {
        String baseSymbol = forex.getCurrencyCode();
        String yahooSymbol = baseSymbol + "=X";
        try {
            YahooQuoteDto quote = yahooForexClient.fetchQuote(yahooSymbol);
            if (quote != null && quote.regularMarketPrice() != null) {
                forexMapper.applyYahooSnapshot(forex, quote, LocalDateTime.now());
                forexRepository.save(forex);
                forexCacheService.putSnapshot(baseSymbol, forex);
                return;
            }
        } catch (Exception e) {
            log.warn("Direct fetch failed for {}, trying synthetic", baseSymbol, e);
        }
        if (!"USDTRY".equals(baseSymbol)) {
            trySyntheticSnapshot(forex);
        } else {
            throw new ExternalApiException("Yahoo Finance", "All snapshot attempts failed for " + baseSymbol);
        }
    }
    @Transactional
    public void updateForexCandles(Forex forex, Map<String, ForexCandle> usdtryCandleMap) {
        String baseSymbol = forex.getCurrencyCode();
        String yahooSymbol = baseSymbol + "=X";
        long candleCount = forexCandleRepository.countByCurrencyCode(baseSymbol);
        String range = candleCount >= MIN_CANDLES_FOR_INCREMENTAL ? "1mo" : "5y";
        String interval = "1d";
        try {
            List<YahooCandleDto> candles = yahooForexClient.fetchCandles(yahooSymbol, range, interval);
            if (!candles.isEmpty()) {
                int saved = saveCandleBatch(forex, candles);
                if (saved >= 100 || "1mo".equals(range)) {
                    return;
                }
                if ("5y".equals(range)) {
                    log.warn("Only {} candles for {}, trying synthetic", saved, baseSymbol);
                }
            }
        } catch (Exception e) {
            log.warn("Direct candle fetch failed for {}, trying synthetic", baseSymbol, e);
        }
        if (!"USDTRY".equals(baseSymbol)) {
            trySyntheticCandles(forex, usdtryCandleMap);
        } else {
            throw new ExternalApiException("Yahoo Finance", "All candle attempts failed for " + baseSymbol);
        }
    }
    private void trySyntheticSnapshot(Forex forex) {
        Forex usdtry = forexRepository.findById("USDTRY").orElse(null);
        if (usdtry == null || usdtry.getCurrentPrice() == null) {
            throw new ExternalApiException("Yahoo Finance",
                    "USDTRY not available for synthetic calculation of " + forex.getCurrencyCode());
        }
        String baseCurrency = forex.getCurrencyCode().replace("TRY", "");
        String[] attempts = { baseCurrency + "USD=X", "USD" + baseCurrency + "=X" };
        for (String symbol : attempts) {
            try {
                YahooQuoteDto pairQuote = yahooForexClient.fetchQuote(symbol);
                if (pairQuote != null && pairQuote.regularMarketPrice() != null) {
                    boolean isUsdBase = symbol.startsWith("USD");
                    forexMapper.applySyntheticSnapshot(forex, pairQuote,
                            usdtry.getCurrentPrice(), usdtry.getChange24h(), isUsdBase,
                            LocalDateTime.now()); 
                    forexRepository.save(forex);
                    forexCacheService.putSnapshot(forex.getCurrencyCode(), forex);
                    return;
                }
            } catch (Exception e) {
                log.warn("Synthetic {} failed for {}", symbol, forex.getCurrencyCode(), e);
            }
        }
        throw new ExternalApiException("Yahoo Finance",
                "All snapshot attempts failed for " + forex.getCurrencyCode());
    }
    private void trySyntheticCandles(Forex forex, Map<String, ForexCandle> usdtryCandleMap) {
        if (usdtryCandleMap.isEmpty()) {
            throw new ExternalApiException("Yahoo Finance",
                    "USDTRY candles not available for " + forex.getCurrencyCode());
        }
        String baseCurrency = forex.getCurrencyCode().replace("TRY", "");
        String[] attempts = { baseCurrency + "USD=X", "USD" + baseCurrency + "=X" };
        for (String symbol : attempts) {
            try {
                List<YahooCandleDto> pairCandles = yahooForexClient.fetchCandles(symbol, "5y", "1d");
                if (!pairCandles.isEmpty()) {
                    boolean isUsdBase = symbol.startsWith("USD");
                    List<YahooCandleDto> syntheticCandles = forexMapper.buildSyntheticCandles(
                            pairCandles, usdtryCandleMap, forex.getCurrentPrice(), isUsdBase);
                    int saved = saveCandleBatch(forex, syntheticCandles);
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
    private int saveCandleBatch(Forex forex, List<YahooCandleDto> candleDtos) {
        List<LocalDateTime> dates = candleDtos.stream().map(YahooCandleDto::candleDate).toList();
        Map<LocalDateTime, ForexCandle> existingMap = forexCandleRepository
                .findByCurrencyCodeAndCandleDateIn(forex.getCurrencyCode(), dates)
                .stream()
                .collect(Collectors.toMap(
                        ForexCandle::getCandleDate,
                        Function.identity(),
                        (a, b) -> a));
        List<ForexCandle> toSave = new ArrayList<>(candleDtos.size());
        int updateCount = 0;
        for (YahooCandleDto dto : candleDtos) {
            ForexCandle existing = existingMap.get(dto.candleDate());
            if (existing != null) {
                forexMapper.updateCandleEntity(existing, dto);
                updateCount++;
            } else {
                toSave.add(forexMapper.toCandleEntity(dto, forex.getCurrencyCode(), forex));
            }
        }
        if (!toSave.isEmpty()) {
            forexCandleRepository.saveAll(toSave);
        }
        return toSave.size() + updateCount;
    }

    @Transactional
    public void pruneOldForexCandles() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(YEARS_TO_KEEP);
        forexCandleRepository.deleteByCandleDateBefore(cutoffDate);
    }
}
