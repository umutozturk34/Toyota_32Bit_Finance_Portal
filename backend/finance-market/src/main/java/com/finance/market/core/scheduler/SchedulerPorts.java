package com.finance.market.core.scheduler;

import com.finance.shared.event.EventPublisherPort;
import com.finance.market.core.service.MarketUpdatePort;
import com.finance.shared.service.PortfolioSnapshotPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Optional outbound ports a market scheduler fires after a refresh (portfolio snapshot, market
 * cache, event publish); each is absent when its module is not on the classpath.
 */
@Component
public record SchedulerPorts(
        Optional<PortfolioSnapshotPort> portfolio,
        Optional<MarketUpdatePort> market,
        Optional<EventPublisherPort> events
) {
}
