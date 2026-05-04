package com.finance.common.scheduler;

import com.finance.common.event.MarketUpdateEventPort;
import com.finance.common.service.MarketUpdatePort;
import com.finance.common.service.PortfolioSnapshotPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public record SchedulerPorts(
        Optional<PortfolioSnapshotPort> portfolio,
        Optional<MarketUpdatePort> market,
        Optional<MarketUpdateEventPort> events
) {
}
