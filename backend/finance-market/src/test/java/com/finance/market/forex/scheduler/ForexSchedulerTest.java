package com.finance.market.forex.scheduler;

import com.finance.common.model.MarketType;
import com.finance.market.core.scheduler.SchedulerPorts;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.market.forex.service.ForexDataService;
import com.finance.shared.service.PortfolioSnapshotPort;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ForexSchedulerTest {

    private ForexDataService dataService;
    private TaskTrackingService taskTracker;
    private PortfolioSnapshotPort portfolioPort;
    private MarketUpdatePort marketPort;
    private ForexScheduler scheduler;

    @BeforeEach
    void setUp() {
        dataService = mock(ForexDataService.class);
        taskTracker = mock(TaskTrackingService.class);
        portfolioPort = mock(PortfolioSnapshotPort.class);
        marketPort = mock(MarketUpdatePort.class);
        scheduler = new ForexScheduler(dataService, taskTracker,
                new SchedulerPorts(Optional.of(portfolioPort), Optional.of(marketPort), Optional.empty()));

        doAnswer(inv -> {
            Runnable r = inv.getArgument(2);
            r.run();
            return null;
        }).when(taskTracker).runTracked(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    void should_triggerRefreshAllAndPorts_when_dailyCron() {
        scheduler.runDailyForexUpdate();

        verify(dataService).refreshAll();
        verify(portfolioPort).onMarketUpdate(MarketType.FOREX);
        verify(marketPort).onMarketDataUpdated(MarketType.FOREX);
    }
}
