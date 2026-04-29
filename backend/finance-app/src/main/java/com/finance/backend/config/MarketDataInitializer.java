package com.finance.backend.config;

import com.finance.backend.repository.*;
import com.finance.backend.service.*;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Log4j2
@Component
@Order(1)
@RequiredArgsConstructor
public class MarketDataInitializer implements CommandLineRunner {

    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final CryptoDataService cryptoDataService;
    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final StockDataService stockDataService;
    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final FundDataService fundDataService;
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final ForexDataService forexDataService;
    private final TcmbForexService tcmbForexService;
    private final CommodityRepository commodityRepository;
    private final CommodityCandleRepository commodityCandleRepository;
    private final CommodityDataService commodityDataService;
    private final BondRepository bondRepository;
    private final BondDataService bondDataService;
    private final NewsArticleRepository articleRepository;
    private final NewsDataService newsDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;

    @Override
    public void run(String... args) {
        init("crypto", cryptoRepository.count(), cryptoCandleRepository.count(), null, () -> {
            cryptoDataService.refreshAllSnapshots();
            cryptoDataService.refreshAllCandles();
        });

        init("fund", fundRepository.count(), fundCandleRepository.count(), null, () -> {
            fundDataService.refreshAllSnapshots();
            fundDataService.refreshAllCandles();
        });

        init("bond", bondRepository.count(), 1, null, bondDataService::updateBonds);

        init("news", articleRepository.count(), 1, null, newsDataService::updateNews);

        CompletableFuture<Void> forexFuture = init(
                "forex", forexRepository.count(), forexCandleRepository.count(), null, () -> {
            tcmbForexService.fetchAndSaveTcmbRates();
            forexDataService.syncAllYahooSnapshots();
            forexDataService.syncAllYahooCandles();
        });

        CompletableFuture<Void> stockFuture = init(
                "stock", stockRepository.count(), stockCandleRepository.count(), forexFuture, () -> {
            stockDataService.refreshAllSnapshots();
            stockDataService.refreshAllCandles();
        });

        init("commodity", commodityRepository.count(), commodityCandleRepository.count(), stockFuture, () -> {
            commodityDataService.refreshAllSnapshots();
            commodityDataService.refreshAllCandles();
        });
    }

    private CompletableFuture<Void> init(String name, long snapshotCount, long candleCount,
                                         CompletableFuture<?> prerequisite, Runnable action) {
        if (snapshotCount > 0 && candleCount > 0) {
            log.info("{} data exists - skipping init", name);
            return CompletableFuture.completedFuture(null);
        }
        log.info("No {} data - starting initial fetch", name);
        TaskInfo started = taskTracker.startTask("init-" + name, "Initial " + name + " data fetch");
        return CompletableFuture.runAsync(() -> {
            try {
                if (prerequisite != null) {
                    prerequisite.handle((r, ex) -> null).join();
                }
                action.run();
                taskTracker.completeTask("init-" + name, started);
                log.info("{} init completed", name);
            } catch (Exception e) {
                taskTracker.failTask("init-" + name, started, e.getMessage());
                log.error("{} init failed: {}", name, e.getMessage(), e);
            }
        }, taskExecutor);
    }
}
