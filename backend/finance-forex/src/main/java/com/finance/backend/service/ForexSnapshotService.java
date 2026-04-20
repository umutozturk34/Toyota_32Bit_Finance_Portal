package com.finance.backend.service;

import com.finance.backend.client.YahooForexClient;
import com.finance.backend.dto.external.YahooQuoteDto;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
@Log4j2
public class ForexSnapshotService implements SnapshotBatchRefresher {

    private final YahooForexClient yahooForexClient;
    private final PriceCalculationService priceCalculationService;
    private final ForexRepository forexRepository;
    private final MarketCacheService<Forex, ForexCandle> forexCacheService;
    private final TransactionTemplate transactionTemplate;

    public ForexSnapshotService(YahooForexClient yahooForexClient,
                                     PriceCalculationService priceCalculationService,
                                     ForexRepository forexRepository,
                                     MarketCacheService<Forex, ForexCandle> forexCacheService,
                                     PlatformTransactionManager transactionManager) {
        this.yahooForexClient = yahooForexClient;
        this.priceCalculationService = priceCalculationService;
        this.forexRepository = forexRepository;
        this.forexCacheService = forexCacheService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.FOREX;
    }

    @Override
    public void refreshAll() {
        List<Forex> allForex = forexRepository.findAll();
        log.info("Starting Yahoo forex snapshot sync for {} pairs", allForex.size());

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                allForex,
                this::updateForexSnapshot,
                Forex::getCurrencyCode,
                "snapshot",
                5,
                (forex, e) -> log.error("Snapshot failed for {}: {}", forex.getCurrencyCode(), e.getMessage(), e),
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("Yahoo CB is OPEN, stopping snapshot sync. {} success, {} failed so far",
                        stopped.successCount(), stopped.failCount()));

        BatchLogHelper.logSummary(log, "Yahoo snapshot sync", result);
    }

    private void updateForexSnapshot(Forex forex) {
        String baseSymbol = forex.getCurrencyCode();
        String yahooSymbol = baseSymbol + "=X";
        try {
            YahooQuoteDto quote = yahooForexClient.fetchQuote(yahooSymbol);
            if (quote != null && quote.regularMarketPrice() != null) {
                transactionTemplate.executeWithoutResult(status -> {
                    priceCalculationService.applyDirectSnapshot(forex, quote);
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
                        priceCalculationService.applySyntheticSnapshot(forex, pairQuote,
                                usdtry.getCurrentPrice(), usdtry.getChange24h(), isUsdBase);
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
}
