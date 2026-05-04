package com.finance.backend.scheduler;

import com.finance.backend.model.MarketType;
import com.finance.backend.service.CommodityDataService;
import com.finance.backend.service.MarketUpdatePort;
import com.finance.backend.service.PortfolioSnapshotPort;
import com.finance.backend.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommoditySchedulerTest {

    private CommodityDataService dataService;
    private TaskTrackingService taskTracker;
    private PortfolioSnapshotPort portfolioPort;
    private MarketUpdatePort marketPort;
    private CommodityScheduler scheduler;

    @BeforeEach
    void setUp() {
        dataService = mock(CommodityDataService.class);
        taskTracker = mock(TaskTrackingService.class);
        portfolioPort = mock(PortfolioSnapshotPort.class);
        marketPort = mock(MarketUpdatePort.class);
        scheduler = new CommodityScheduler(dataService, taskTracker,
                new SchedulerPorts(Optional.of(portfolioPort), Optional.of(marketPort), Optional.empty()));

        doAnswer(inv -> {
            Runnable r = inv.getArgument(2);
            r.run();
            return null;
        }).when(taskTracker).runTracked(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    void runMorningTriggersSnapshotCandlesAndPorts() {
        scheduler.runMorningCommodityUpdate();

        verify(dataService).refreshAll();
        verify(portfolioPort).onMarketUpdate(MarketType.COMMODITY);
        verify(marketPort).onMarketDataUpdated(MarketType.COMMODITY);
    }

    @Test
    void runAfternoonTriggersFullPipeline() {
        scheduler.runAfternoonCommodityUpdate();

        verify(dataService).refreshAll();
    }

    @Test
    void runEveningTriggersFullPipeline() {
        scheduler.runEveningCommodityUpdate();

        verify(dataService).refreshAll();
    }

}
