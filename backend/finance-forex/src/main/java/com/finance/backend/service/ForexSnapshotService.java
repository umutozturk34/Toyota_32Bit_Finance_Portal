package com.finance.backend.service;

import com.finance.backend.client.YahooForexClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.config.ForexProperties;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.MarketBatchRunner;
import com.finance.backend.util.SyntheticPriceCalculator;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

@Service
@Log4j2
public class ForexSnapshotService implements SnapshotBatchRefresher {

    private final YahooForexClient yahooForexClient;
    private final ForexRepository forexRepository;
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final TransactionTemplate transactionTemplate;
    private final int scale;
    private final BigDecimal spreadRate;

    public ForexSnapshotService(YahooForexClient yahooForexClient,
                                ForexRepository forexRepository,
                                MarketCacheService<Forex, ForexCandle> forexCacheService,
                                TransactionTemplate transactionTemplate,
                                AppProperties appProperties,
                                ForexProperties forexProperties) {
        this.yahooForexClient = yahooForexClient;
        this.forexRepository = forexRepository;
        this.forexCacheService = forexCacheService;
        this.transactionTemplate = transactionTemplate;
        this.scale = appProperties.getScale();
        this.spreadRate = forexProperties.getSpreadRate();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.FOREX;
    }

    @Override
    public void refreshAll() {
        List<Forex> allForex = forexRepository.findAll();
        log.info("Starting Yahoo forex snapshot sync for {} pairs", allForex.size());

        try {
            BatchUpdateRunner.Result result = MarketBatchRunner.run(
                    allForex,
                    this::updateForexSnapshot,
                    Forex::getCurrencyCode,
                    log, "Forex", "snapshot", 5);
            BatchLogHelper.logSummary(log, "Yahoo snapshot sync", result);
        } catch (BusinessException e) {
            log.warn("Yahoo forex snapshot best-effort failed (TCMB remains primary): {}", e.getMessage());
        }
    }

    @Override
    public void refreshSnapshot(String code) {
        Forex forex = forexRepository.findById(code).orElse(null);
        if (forex == null) {
            log.warn("Forex pair {} not found for single-code refresh", code);
            return;
        }
        updateForexSnapshot(forex);
    }

    private void updateForexSnapshot(Forex forex) {
        String baseSymbol = forex.getCurrencyCode();
        String yahooSymbol = baseSymbol + "=X";
        try {
            YahooQuoteDto quote = yahooForexClient.fetchQuote(yahooSymbol);
            if (quote != null && quote.regularMarketPrice() != null) {
                transactionTemplate.executeWithoutResult(status -> {
                    forex.applyYahooSnapshot(
                            quote.regularMarketPrice(), quote.previousClose(),
                            quote.openPrice(), quote.dayHigh(), quote.dayLow(), quote.volume(),
                            spreadRate, scale);
                    forexRepository.save(forex);
                });
                forexCacheService.putSnapshot(baseSymbol, forex);
                return;
            }
        } catch (CallNotPermittedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Direct fetch failed for {}, trying synthetic", baseSymbol, e);
        }
        if (!"USDTRY".equals(baseSymbol)) {
            trySyntheticSnapshot(forex);
        } else {
            throw new ExternalApiException("Yahoo Finance", "All snapshot attempts failed for " + baseSymbol);
        }
    }

    private void trySyntheticSnapshot(Forex forex) {
        Forex usdtry = forexRepository.findById("USDTRY").orElse(null);
        if (usdtry == null || usdtry.getCurrentPrice() == null) {
            throw new ExternalApiException("Yahoo Finance",
                    "USDTRY not available for synthetic calculation of " + forex.getCurrencyCode());
        }
        String baseCurrency = forex.getCurrencyCode().replace("TRY", "");
        String[] attempts = {baseCurrency + "USD=X", "USD" + baseCurrency + "=X"};
        for (String symbol : attempts) {
            try {
                YahooQuoteDto pairQuote = yahooForexClient.fetchQuote(symbol);
                if (pairQuote != null && pairQuote.regularMarketPrice() != null) {
                    boolean isUsdBase = symbol.startsWith("USD");
                    transactionTemplate.executeWithoutResult(status -> {
                        applySyntheticSnapshot(forex, pairQuote, usdtry.getCurrentPrice(),
                                usdtry.getChangeAmount(), isUsdBase);
                        forexRepository.save(forex);
                    });
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

    private void applySyntheticSnapshot(Forex forex, YahooQuoteDto pairQuote,
                                        BigDecimal usdtryPrice, BigDecimal usdtryChange,
                                        boolean isUsdBase) {
        BigDecimal syntheticPrice = SyntheticPriceCalculator.calculateSyntheticPrice(
                pairQuote.regularMarketPrice(), usdtryPrice, isUsdBase, scale);
        if (syntheticPrice == null) return;
        BigDecimal syntheticPreviousClose = SyntheticPriceCalculator.calculateSyntheticPreviousClose(
                pairQuote.previousClose(), usdtryPrice, usdtryChange, isUsdBase, scale);
        forex.applySyntheticPrice(syntheticPrice, syntheticPreviousClose, spreadRate, scale);
    }
}
