package com.finance.backend.scheduler;

import com.finance.backend.event.MarketUpdateEventPort;
import com.finance.backend.service.MarketUpdatePort;
import com.finance.backend.service.PortfolioSnapshotPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public record SchedulerPorts(
        Optional<PortfolioSnapshotPort> portfolio,
        Optional<MarketUpdatePort> market,
        Optional<MarketUpdateEventPort> events
) {
}
