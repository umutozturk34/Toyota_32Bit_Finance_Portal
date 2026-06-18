package com.finance.app.config;

import com.finance.market.bond.repository.BondRepository;
import com.finance.market.bond.service.BondDataService;
import com.finance.market.commodity.repository.CommodityCandleRepository;
import com.finance.market.commodity.repository.CommodityRepository;
import com.finance.market.commodity.service.CommodityDataService;
import com.finance.market.core.client.EvdsCredentials;
import com.finance.market.core.scheduler.SchedulerPorts;
import com.finance.market.crypto.repository.CryptoCandleRepository;
import com.finance.market.crypto.repository.CryptoRepository;
import com.finance.market.crypto.service.CryptoDataService;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.finance.market.forex.repository.ForexRepository;
import com.finance.market.forex.service.ForexDataService;
import com.finance.market.fund.repository.FundCandleRepository;
import com.finance.market.fund.repository.FundRepository;
import com.finance.market.fund.service.FundDataService;
import com.finance.market.macro.repository.MacroIndicatorPointRepository;
import com.finance.market.macro.repository.MacroIndicatorRepository;
import com.finance.market.macro.service.MacroIndicatorFetchService;
import com.finance.market.macro.service.MacroIndicatorRegistryService;
import com.finance.market.stock.repository.StockCandleRepository;
import com.finance.market.stock.repository.StockRepository;
import com.finance.market.stock.service.StockDataService;
import com.finance.market.viop.repository.ViopContractRepository;
import com.finance.market.viop.service.ViopDataService;
import com.finance.news.repository.NewsArticleRepository;
import com.finance.news.service.article.NewsDataService;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketDataInitializerTest {

    @Mock private CryptoRepository cryptoRepository;
    @Mock private CryptoCandleRepository cryptoCandleRepository;
    @Mock private CryptoDataService cryptoDataService;
    @Mock private StockRepository stockRepository;
    @Mock private StockCandleRepository stockCandleRepository;
    @Mock private StockDataService stockDataService;
    @Mock private FundRepository fundRepository;
    @Mock private FundCandleRepository fundCandleRepository;
    @Mock private FundDataService fundDataService;
    @Mock private ForexRepository forexRepository;
    @Mock private ForexCandleRepository forexCandleRepository;
    @Mock private ForexDataService forexDataService;
    @Mock private CommodityRepository commodityRepository;
    @Mock private CommodityCandleRepository commodityCandleRepository;
    @Mock private CommodityDataService commodityDataService;
    @Mock private BondRepository bondRepository;
    @Mock private BondDataService bondDataService;
    @Mock private MacroIndicatorRepository macroIndicatorRepository;
    @Mock private MacroIndicatorPointRepository macroIndicatorPointRepository;
    @Mock private MacroIndicatorRegistryService macroRegistry;
    @Mock private MacroIndicatorFetchService macroFetcher;
    @Mock private ViopContractRepository viopContractRepository;
    @Mock private ViopDataService viopDataService;
    @Mock private NewsArticleRepository articleRepository;
    @Mock private NewsDataService newsDataService;
    @Mock private TaskTrackingService taskTracker;
    @Mock private SchedulerPorts ports;

    private final List<String> order = new CopyOnWriteArrayList<>();
    private MarketDataInitializer initializer;

    @BeforeEach
    void setUp() {
        when(ports.portfolio()).thenReturn(Optional.empty());
        when(ports.market()).thenReturn(Optional.empty());
        doAnswer(record("crypto")).when(cryptoDataService).refreshAll();
        doAnswer(record("fund")).when(fundDataService).refreshAll();
        doAnswer(record("bond")).when(bondDataService).updateBonds();
        doAnswer(record("macro")).when(macroFetcher).refreshAll();
        doAnswer(record("viop")).when(viopDataService).refreshAll();
        doAnswer(record("news")).when(newsDataService).updateNews();
        doAnswer(record("forex")).when(forexDataService).refreshAll();
        doAnswer(record("stock")).when(stockDataService).refreshAll();
        doAnswer(record("commodity")).when(commodityDataService).refreshAll();
        Executor synchronous = Runnable::run;
        initializer = new MarketDataInitializer(
                cryptoRepository, cryptoCandleRepository, cryptoDataService,
                stockRepository, stockCandleRepository, stockDataService,
                fundRepository, fundCandleRepository, fundDataService,
                forexRepository, forexCandleRepository, forexDataService,
                commodityRepository, commodityCandleRepository, commodityDataService,
                bondRepository, bondDataService,
                macroIndicatorRepository, macroIndicatorPointRepository, macroRegistry, macroFetcher,
                viopContractRepository, viopDataService,
                articleRepository, newsDataService,
                taskTracker, synchronous, ports, new EvdsCredentials("test-evds-key"));
    }

    private Answer<Void> record(String name) {
        return invocation -> {
            order.add(name);
            return null;
        };
    }

    @Test
    void should_fetchEveryAssetClass_when_databaseIsEmpty() {
        // Arrange — every repository count defaults to 0, so all data is treated as missing

        // Act
        initializer.run(new String[]{});

        // Assert
        assertThat(order).containsExactlyInAnyOrder(
                "crypto", "fund", "bond", "macro", "viop", "news", "forex", "stock", "commodity");
    }

    @Test
    void should_keepForexBeforeStockBeforeCommodity_when_fetchingTheDependentGroup() {
        // Act
        initializer.run(new String[]{});

        // Assert
        assertThat(order.indexOf("forex")).isLessThan(order.indexOf("stock"));
        assertThat(order.indexOf("stock")).isLessThan(order.indexOf("commodity"));
    }

    @Test
    void should_skipAssetClass_when_itsDataAlreadyExists() {
        // Arrange — crypto already has both snapshot and candle data
        when(cryptoRepository.count()).thenReturn(5L);
        when(cryptoCandleRepository.count()).thenReturn(50L);

        // Act
        initializer.run(new String[]{});

        // Assert
        verify(cryptoDataService, never()).refreshAll();
        assertThat(order).doesNotContain("crypto").contains("forex", "stock", "commodity");
    }

    @Test
    void should_completeTheInitFuture_when_runFinishes() {
        // Act
        initializer.run(new String[]{});

        // Assert — the completion future is exposed and done so late warmers (e.g. the beater cache) can gate on it.
        assertThat(initializer.completion()).isDone();
    }

    @Test
    void should_failInit_when_fetchPersistsNoData() {
        // Arrange — forex runs but its repositories stay empty afterwards (counts default to 0), which is what a
        // swallowed upstream fetch failure looks like: the action returns normally yet writes nothing.

        // Act
        initializer.run(new String[]{});

        // Assert — the empty fetch is reported as a failure, never as a completed init, so the "forex completed but
        // no data" false-success can no longer cascade into the FX-dependent stock/commodity fetches.
        verify(taskTracker).failTask(eq("init-forex"), any(), anyString());
        verify(taskTracker, never()).completeTask(eq("init-forex"), any());
    }

    @Test
    void should_reportApiKeyMissing_forEvdsBackedClasses_whenEvdsKeyAbsent() {
        // Arrange — the EVDS API key is absent, so the EVDS-backed classes (forex, bond, macro) cannot fetch.
        MarketDataInitializer noKey = new MarketDataInitializer(
                cryptoRepository, cryptoCandleRepository, cryptoDataService,
                stockRepository, stockCandleRepository, stockDataService,
                fundRepository, fundCandleRepository, fundDataService,
                forexRepository, forexCandleRepository, forexDataService,
                commodityRepository, commodityCandleRepository, commodityDataService,
                bondRepository, bondDataService,
                macroIndicatorRepository, macroIndicatorPointRepository, macroRegistry, macroFetcher,
                viopContractRepository, viopDataService,
                articleRepository, newsDataService,
                taskTracker, Runnable::run, ports, new EvdsCredentials(""));

        // Act
        noKey.run(new String[]{});

        // Assert — EVDS-backed fetches are NOT attempted (no doomed request / timeout) and are reported with the
        // distinct API_KEY_MISSING status carrying an actionable message, while non-EVDS classes still run.
        verify(forexDataService, never()).refreshAll();
        verify(bondDataService, never()).updateBonds();
        verify(macroFetcher, never()).refreshAll();
        verify(taskTracker).failApiKeyMissing(eq("init-forex"), any(), anyString());
        verify(taskTracker).failApiKeyMissing(eq("init-bond"), any(), anyString());
        verify(taskTracker).failApiKeyMissing(eq("init-macro"), any(), anyString());
        assertThat(order).contains("crypto");
    }
}
