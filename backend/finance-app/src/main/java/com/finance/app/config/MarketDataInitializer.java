package com.finance.app.config;
import com.finance.market.forex.service.TcmbForexService;

import com.finance.shared.service.TaskTrackingService;

import com.finance.market.stock.repository.StockRepository;

import com.finance.market.stock.service.StockDataService;

import com.finance.market.stock.repository.StockCandleRepository;

import com.finance.news.service.article.NewsDataService;

import com.finance.news.repository.NewsArticleRepository;

import com.finance.market.fund.repository.FundRepository;

import com.finance.market.fund.service.FundDataService;

import com.finance.market.fund.repository.FundCandleRepository;

import com.finance.market.forex.repository.ForexRepository;

import com.finance.market.forex.service.ForexDataService;

import com.finance.market.forex.repository.ForexCandleRepository;

import com.finance.market.crypto.repository.CryptoRepository;

import com.finance.market.crypto.service.CryptoDataService;

import com.finance.market.crypto.repository.CryptoCandleRepository;

import com.finance.market.commodity.repository.CommodityRepository;

import com.finance.market.commodity.service.CommodityDataService;

import com.finance.market.commodity.repository.CommodityCandleRepository;

import com.finance.market.bond.repository.BondRepository;

import com.finance.market.bond.service.BondDataService;



import com.finance.common.model.MarketType;
import com.finance.market.core.scheduler.SchedulerPorts;
import com.finance.shared.service.TaskTrackingService.TaskInfo;
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
    private final SchedulerPorts ports;

    @Override
    public void run(String... args) {
        init("crypto", MarketType.CRYPTO, cryptoRepository.count(), cryptoCandleRepository.count(), null,
                cryptoDataService::refreshAll);

        init("fund", MarketType.FUND, fundRepository.count(), fundCandleRepository.count(), null,
                fundDataService::refreshAll);

        init("bond", null, bondRepository.count(), 1, null, bondDataService::updateBonds);

        init("news", null, articleRepository.count(), 1, null, newsDataService::updateNews);

        CompletableFuture<Void> forexFuture = init(
                "forex", MarketType.FOREX, forexRepository.count(), forexCandleRepository.count(), null, () -> {
            tcmbForexService.fetchAndSaveTcmbRates();
            forexDataService.syncAllYahoo();
        });

        CompletableFuture<Void> stockFuture = init(
                "stock", MarketType.STOCK, stockRepository.count(), stockCandleRepository.count(), forexFuture,
                stockDataService::refreshAll);

        init("commodity", MarketType.COMMODITY, commodityRepository.count(), commodityCandleRepository.count(), stockFuture,
                commodityDataService::refreshAll);
    }

    private CompletableFuture<Void> init(String name, MarketType type, long snapshotCount, long candleCount,
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
                if (type != null) {
                    ports.portfolio().ifPresent(p -> p.onMarketUpdate(type));
                    ports.market().ifPresent(p -> p.onMarketDataUpdated(type));
                }
                taskTracker.completeTask("init-" + name, started);
                log.info("{} init completed", name);
            } catch (Exception e) {
                taskTracker.failTask("init-" + name, started, e.getMessage());
                log.error("{} init failed: {}", name, e.getMessage(), e);
            }
        }, taskExecutor);
    }
}
