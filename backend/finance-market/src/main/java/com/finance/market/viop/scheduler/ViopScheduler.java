package com.finance.market.viop.scheduler;

import com.finance.common.model.MarketType;
import com.finance.market.core.scheduler.AbstractMarketScheduler;
import com.finance.market.core.scheduler.SchedulerPorts;
import com.finance.market.viop.service.ViopDataService;
import com.finance.shared.service.TaskTrackingService;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class ViopScheduler extends AbstractMarketScheduler {

    private final ViopDataService dataService;

    public ViopScheduler(ViopDataService dataService,
                         TaskTrackingService taskTracker,
                         SchedulerPorts ports) {
        super(taskTracker, ports);
        this.dataService = dataService;
    }

    @Override
    protected MarketType marketType() {
        return MarketType.VIOP;
    }

    @Override
    protected void runRefresh() {
        dataService.refreshAll();
    }

    @Scheduled(cron = "${app.scheduler.viop.open-cron:0 35 9 * * MON-FRI}", zone = "${app.timezone}")
    public void runOpenUpdate() {
        executeMarketUpdate("scheduled-viop-open", "Scheduled VIOP open refresh (09:35)");
    }

    @Scheduled(cron = "${app.scheduler.viop.midday-cron:0 0 13 * * MON-FRI}", zone = "${app.timezone}")
    public void runMiddayUpdate() {
        executeMarketUpdate("scheduled-viop-midday", "Scheduled VIOP midday refresh (13:00)");
    }

    @Scheduled(cron = "${app.scheduler.viop.afternoon-cron:0 0 16 * * MON-FRI}", zone = "${app.timezone}")
    public void runAfternoonUpdate() {
        executeMarketUpdate("scheduled-viop-afternoon", "Scheduled VIOP afternoon refresh (16:00)");
    }

    @Scheduled(cron = "${app.scheduler.viop.close-cron:0 15 18 * * MON-FRI}", zone = "${app.timezone}")
    public void runCloseUpdate() {
        executeMarketUpdate("scheduled-viop-close", "Scheduled VIOP close refresh (18:15)");
    }
}
