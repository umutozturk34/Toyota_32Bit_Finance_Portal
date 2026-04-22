package com.finance.backend.config;

import com.finance.backend.repository.*;
import com.finance.backend.service.*;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
        initIfEmpty("crypto", cryptoRepository.count(), cryptoCandleRepository.count(), () -> {
            cryptoDataService.refreshAllSnapshots();
            cryptoDataService.refreshAllCandles();
        });

        initIfEmpty("stock", stockRepository.count(), stockCandleRepository.count(), () -> {
            stockDataService.refreshAllSnapshots();
            stockDataService.refreshAllCandles();
        });

        initIfEmpty("fund", fundRepository.count(), fundCandleRepository.count(), () -> {
            fundDataService.refreshAllSnapshots();
            fundDataService.refreshAllCandles();
        });

        initIfEmpty("commodity", commodityRepository.count(), commodityCandleRepository.count(), () -> {
            commodityDataService.refreshAllSnapshots();
            commodityDataService.refreshAllCandles();
        });

        initIfEmpty("bond", bondRepository.count(), 1, bondDataService::updateBonds);

        initIfEmpty("news", articleRepository.count(), 1, newsDataService::updateNews);
    }

    private void initIfEmpty(String name, long snapshotCount, long candleCount, Runnable action) {
        if (snapshotCount > 0 && candleCount > 0) {
            log.info("{} data exists - skipping init", name);
            return;
        }
        log.info("No {} data - starting initial fetch", name);
        TaskInfo started = taskTracker.startTask("init-" + name, "Initial " + name + " data fetch");
        taskExecutor.execute(() -> {
            try {
                action.run();
                taskTracker.completeTask("init-" + name, started);
                log.info("{} init completed", name);
            } catch (Exception e) {
                taskTracker.failTask("init-" + name, started, e.getMessage());
                log.error("{} init failed: {}", name, e.getMessage(), e);
            }
        });
    }
}
