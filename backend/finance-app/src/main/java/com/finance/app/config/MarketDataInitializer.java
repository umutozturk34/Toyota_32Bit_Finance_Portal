package com.finance.app.config;

import com.finance.shared.service.TaskTrackingService;

import io.opentelemetry.instrumentation.annotations.WithSpan;

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

import com.finance.market.macro.repository.MacroIndicatorRepository;

import com.finance.market.macro.repository.MacroIndicatorPointRepository;

import com.finance.market.macro.service.MacroIndicatorRegistryService;

import com.finance.market.macro.service.MacroIndicatorFetchService;

import com.finance.market.viop.repository.ViopContractRepository;

import com.finance.market.viop.service.ViopDataService;



import com.finance.common.market.MarketDataReadiness;
import com.finance.common.model.MarketType;
import com.finance.market.core.scheduler.SchedulerPorts;
import com.finance.shared.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * On startup, seeds market data for each asset class only when its tables are empty (idempotent
 * cold-start fetch), running fetches asynchronously. Forex is fetched before stocks/commodities since
 * those depend on FX rates; after each fetch it notifies the portfolio and market-cache ports.
 * Set {@code app.market.init.enabled=false} (env {@code APP_MARKET_INIT_ENABLED}) to skip it entirely.
 */
@Log4j2
@Component
@Order(1)
@ConditionalOnProperty(name = "app.market.init.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MarketDataInitializer implements CommandLineRunner, MarketDataReadiness {

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
    private final CommodityRepository commodityRepository;
    private final CommodityCandleRepository commodityCandleRepository;
    private final CommodityDataService commodityDataService;
    private final BondRepository bondRepository;
    private final BondDataService bondDataService;
    private final MacroIndicatorRepository macroIndicatorRepository;
    private final MacroIndicatorPointRepository macroIndicatorPointRepository;
    private final MacroIndicatorRegistryService macroRegistry;
    private final MacroIndicatorFetchService macroFetcher;
    private final ViopContractRepository viopContractRepository;
    private final ViopDataService viopDataService;
    private final NewsArticleRepository articleRepository;
    private final NewsDataService newsDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;
    private final SchedulerPorts ports;

    // Completes when the whole cold-start fetch chain finishes (or immediately when every asset class already
    // has data). Lets a late warmer — the inflation-beater cache — defer until the base market data is in place
    // instead of flooding a fresh empty DB with external history calls. Defaults to a completed future so a
    // caller that asks before run() (or when init is skipped) never blocks.
    private volatile CompletableFuture<Void> completion = CompletableFuture.completedFuture(null);

    /** Future that completes when the cold-start data load has finished; already-complete on a populated DB. */
    public CompletableFuture<Void> completion() {
        return completion;
    }

    /** Ready once the cold-start load has finished successfully (not blocking, unlike {@link #completion()}). */
    @Override
    public boolean isReady() {
        CompletableFuture<Void> current = completion;
        return current.isDone() && !current.isCompletedExceptionally();
    }

    @Override
    @WithSpan("market-data.coldStartInit")
    public void run(String... args) {
        // Cold-start fetch, throttled into small concurrent groups so a fresh empty database doesn't
        // fire every provider at once and peg the CPU. Independent asset classes run two at a time;
        // FX -> stock -> commodity stay one dependency-ordered group (stock and commodity convert
        // through FX rates), preserved by running them in sequence.
        InitSpec crypto = new InitSpec("crypto", MarketType.CRYPTO, cryptoRepository.count(), cryptoCandleRepository.count(), cryptoDataService::refreshAll);
        InitSpec fund = new InitSpec("fund", MarketType.FUND, fundRepository.count(), fundCandleRepository.count(), fundDataService::refreshAll);
        InitSpec bond = new InitSpec("bond", null, bondRepository.count(), 1, bondDataService::updateBonds);
        InitSpec macro = new InitSpec("macro", null, macroIndicatorRepository.count(), macroIndicatorPointRepository.count(), () -> {
            macroRegistry.synchronizeFromConfig();
            macroFetcher.refreshAll();
        });
        InitSpec viop = new InitSpec("viop", MarketType.VIOP, viopContractRepository.count(), 1, viopDataService::refreshAll);
        InitSpec news = new InitSpec("news", null, articleRepository.count(), 1, newsDataService::updateNews);
        InitSpec forex = new InitSpec("forex", MarketType.FOREX, forexRepository.count(), forexCandleRepository.count(), forexDataService::refreshAll);
        InitSpec stock = new InitSpec("stock", MarketType.STOCK, stockRepository.count(), stockCandleRepository.count(), stockDataService::refreshAll);
        InitSpec commodity = new InitSpec("commodity", MarketType.COMMODITY, commodityRepository.count(), commodityCandleRepository.count(), commodityDataService::refreshAll);

        // News runs LAST, after every market asset (stocks, crypto, forex, commodity, funds) is in the DB — its
        // per-article asset linkage resolves against that data, so resolving it before stocks load (the old order)
        // left fresh articles unlinked to stocks. The startup re-resolution backfill stays as a safety net for
        // matcher/keyword changes, but the steady-state path now links correctly on first ingest.
        completion = runGroup(crypto, fund)
                .thenCompose(v -> runGroup(bond, macro))
                .thenCompose(v -> runGroup(viop, forex))
                .thenCompose(v -> runOne(stock))
                .thenCompose(v -> runOne(commodity))
                .thenCompose(v -> runOne(news));
    }

    private record InitSpec(String name, MarketType type, long snapshotCount, long candleCount, Runnable action) {
    }

    /** Runs every spec in the group concurrently and completes when all of them finish. */
    private CompletableFuture<Void> runGroup(InitSpec... specs) {
        CompletableFuture<?>[] futures = new CompletableFuture<?>[specs.length];
        for (int i = 0; i < specs.length; i++) {
            futures[i] = runOne(specs[i]);
        }
        return CompletableFuture.allOf(futures);
    }

    /**
     * Runs a single spec's fetch asynchronously when its data is missing; tracks the task and notifies
     * dependent ports on success. Returns an already-completed future when the data already exists.
     */
    private CompletableFuture<Void> runOne(InitSpec spec) {
        if (spec.snapshotCount() > 0 && spec.candleCount() > 0) {
            log.info("{} data exists - skipping init", spec.name());
            return CompletableFuture.completedFuture(null);
        }
        log.info("No {} data - starting initial fetch", spec.name());
        TaskInfo started = taskTracker.startTask("init-" + spec.name(), "Initial " + spec.name() + " data fetch");
        return CompletableFuture.runAsync(() -> {
            try {
                spec.action().run();
                if (spec.type() != null) {
                    ports.portfolio().ifPresent(p -> p.onMarketUpdate(spec.type()));
                    ports.market().ifPresent(p -> p.onMarketDataUpdated(spec.type()));
                }
                taskTracker.completeTask("init-" + spec.name(), started);
                log.info("{} init completed", spec.name());
            } catch (Exception e) {
                taskTracker.failTask("init-" + spec.name(), started, e.getMessage());
                log.error("{} init failed: {}", spec.name(), e.getMessage(), e);
            }
        }, taskExecutor);
    }
}
