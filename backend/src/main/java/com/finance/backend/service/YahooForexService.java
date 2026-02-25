package com.finance.backend.service;
import com.finance.backend.client.YahooForexClient;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.mapper.ForexMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.repository.ForexCandleRepository;
import com.finance.backend.repository.ForexRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
@Service
@Slf4j
public class YahooForexService {
    private static final int YEARS_TO_KEEP = 5;
    private static final int MIN_CANDLES_FOR_INCREMENTAL = 1200;
    private final YahooForexClient yahooForexClient;
    private final ForexMapper forexMapper;
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final ForexCacheService forexCacheService;
    public YahooForexService(YahooForexClient yahooForexClient,
                             ForexMapper forexMapper,
                             ForexRepository forexRepository,
                             ForexCandleRepository forexCandleRepository,
                             ForexCacheService forexCacheService) {
        this.yahooForexClient = yahooForexClient;
        this.forexMapper = forexMapper;
        this.forexRepository = forexRepository;
        this.forexCandleRepository = forexCandleRepository;
        this.forexCacheService = forexCacheService;
    }
    public void syncAllYahooSnapshots() {
        log.info("Starting Yahoo Finance snapshot sync...");
        List<Forex> allForex = forexRepository.findAll();
        for (Forex forex : allForex) {
            try {
                updateForexSnapshot(forex);
                Thread.sleep(2000); 
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted during snapshot sync");
                break;
            } catch (Exception e) {
                log.error("[SNAPSHOT] Failed for {}: {}", forex.getCurrencyCode(), e.getMessage());
            }
        }
        log.info("Completed Yahoo Finance snapshot sync");
    }
    public void syncAllYahooCandles() {
        log.info("Starting Yahoo Finance candles sync...");
        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(YEARS_TO_KEEP);
        forexCandleRepository.deleteByCandleDateBefore(cutoffDate);
        List<Forex> allForex = forexRepository.findAll();
        Forex usdtry = allForex.stream()
                .filter(f -> "USDTRY".equals(f.getCurrencyCode()))
                .findFirst()
                .orElse(null);
        if (usdtry == null) {
            log.error("USDTRY not found in database");
            return;
        }
        updateForexCandles(usdtry);
        forexCacheService.clearHistoryCache("USDTRY");
        for (Forex forex : allForex) {
            if ("USDTRY".equals(forex.getCurrencyCode())) {
                continue;
            }
            try {
                updateForexCandles(forex);
                forexCacheService.clearHistoryCache(forex.getCurrencyCode());
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted during candle sync");
                break;
            } catch (Exception e) {
                log.error("[CANDLES] Failed for {}: {}", forex.getCurrencyCode(), e.getMessage());
            }
        }
        log.info("Completed Yahoo Finance candles sync");
    }
    public void updateForexSnapshot(Forex forex) {
        String baseSymbol = forex.getCurrencyCode();
        String yahooSymbol = baseSymbol + "=X";
        try {
            YahooQuoteDto quote = yahooForexClient.fetchQuote(yahooSymbol);
            if (quote != null && quote.regularMarketPrice() != null) {
                forexMapper.applyYahooSnapshot(forex, quote, LocalDateTime.now());
                forexRepository.save(forex);
                forexCacheService.clearSnapshotCache(baseSymbol);
                log.info("[SNAPSHOT] Updated {} price: {}", baseSymbol, quote.regularMarketPrice());
                return;
            }
        } catch (Exception e) {
            log.warn("[SNAPSHOT] Direct fetch failed for {}: {} - trying synthetic", baseSymbol, e.getMessage());
        }
        if (!"USDTRY".equals(baseSymbol)) {
            trySyntheticSnapshot(forex);
        } else {
            log.error("[SNAPSHOT] All attempts failed for {}", baseSymbol);
        }
    }
    public void updateForexCandles(Forex forex) {
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
                    log.info("[CANDLES-DIRECT] Saved {} candles for {}", saved, baseSymbol);
                    return;
                }
                if ("5y".equals(range)) {
                    log.warn("[CANDLES-DIRECT] Only {} candles for {}, trying synthetic", saved, baseSymbol);
                }
            }
        } catch (Exception e) {
            log.warn("[CANDLES] Direct fetch failed for {}: {} - trying synthetic", baseSymbol, e.getMessage());
        }
        if (!"USDTRY".equals(baseSymbol)) {
            trySyntheticCandles(forex);
        } else {
            log.error("[CANDLES] All attempts failed for {}", baseSymbol);
        }
    }
    private void trySyntheticSnapshot(Forex forex) {
        Forex usdtry = forexRepository.findById("USDTRY").orElse(null);
        if (usdtry == null || usdtry.getCurrentPrice() == null) {
            log.error("[SNAPSHOT] USDTRY not available for synthetic calculation");
            return;
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
                    forexCacheService.clearSnapshotCache(forex.getCurrencyCode());
                    log.info("[SNAPSHOT-SYNTHETIC] Updated {} via {} price: {}", forex.getCurrencyCode(), symbol, forex.getCurrentPrice());
                    return;
                }
            } catch (Exception e) {
                log.warn("[SNAPSHOT-SYNTHETIC] {} failed for {}: {}", symbol, forex.getCurrencyCode(), e.getMessage());
            }
        }
        log.error("[SNAPSHOT] All attempts failed for {}", forex.getCurrencyCode());
    }
    private void trySyntheticCandles(Forex forex) {
        Forex usdtry = forexRepository.findById("USDTRY").orElse(null);
        if (usdtry == null) {
            log.error("[CANDLES-SYNTHETIC] USDTRY not available for {}", forex.getCurrencyCode());
            return;
        }
        List<ForexCandle> usdtryCandles = forexCandleRepository
                .findTop1825ByCurrencyCodeOrderByCandleDateDesc("USDTRY");
        if (usdtryCandles.isEmpty()) {
            log.error("[CANDLES-SYNTHETIC] USDTRY candles not available for {}", forex.getCurrencyCode());
            return;
        }
        Map<String, ForexCandle> usdtryCandleByDate = usdtryCandles.stream()
                .collect(Collectors.toMap(
                        c -> c.getCandleDate().toLocalDate().toString(),
                        c -> c,
                        (a, b) -> a));
        String baseCurrency = forex.getCurrencyCode().replace("TRY", "");
        String[] attempts = { baseCurrency + "USD=X", "USD" + baseCurrency + "=X" };
        for (String symbol : attempts) {
            try {
                List<YahooCandleDto> pairCandles = yahooForexClient.fetchCandles(symbol, "5y", "1d");
                if (!pairCandles.isEmpty()) {
                    boolean isUsdBase = symbol.startsWith("USD");
                    List<YahooCandleDto> syntheticCandles = forexMapper.buildSyntheticCandles(
                            pairCandles, usdtryCandleByDate, forex.getCurrentPrice(), isUsdBase);
                    int saved = saveCandleBatch(forex, syntheticCandles);
                    log.info("[CANDLES-SYNTHETIC] Saved {} candles for {} via {}", saved, forex.getCurrencyCode(), symbol);
                    return;
                }
            } catch (Exception e) {
                log.warn("[CANDLES-SYNTHETIC] {} failed for {}: {}", symbol, forex.getCurrencyCode(), e.getMessage());
            }
        }
        log.error("[CANDLES] All attempts failed for {}", forex.getCurrencyCode());
    }
    private int saveCandleBatch(Forex forex, List<YahooCandleDto> candleDtos) {
        Map<LocalDateTime, ForexCandle> existingMap = forexCandleRepository
                .findByCurrencyCodeOrderByCandleDateDesc(forex.getCurrencyCode())
                .stream()
                .collect(Collectors.toMap(
                        ForexCandle::getCandleDate,
                        Function.identity(),
                        (a, b) -> a));
        List<ForexCandle> toSave = new ArrayList<>(candleDtos.size());
        for (YahooCandleDto dto : candleDtos) {
            ForexCandle existing = existingMap.get(dto.candleDate());
            if (existing != null) {
                forexMapper.updateCandleEntity(existing, dto);
                toSave.add(existing);
            } else {
                toSave.add(forexMapper.toCandleEntity(dto, forex.getCurrencyCode(), forex));
            }
        }
        if (!toSave.isEmpty()) {
            forexCandleRepository.saveAll(toSave);
        }
        return toSave.size();
    }
}
