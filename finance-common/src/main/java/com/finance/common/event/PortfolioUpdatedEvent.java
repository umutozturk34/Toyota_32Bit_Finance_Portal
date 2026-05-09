package com.finance.common.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PortfolioUpdatedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        String userSub,
        Long portfolioId,
        Long snapshotId,
        BigDecimal totalValue,
        BigDecimal dailyPnl,
        BigDecimal dailyPnlPercent
) implements DomainEvent {

    public static PortfolioUpdatedEvent of(String userSub,
                                           Long portfolioId,
                                           Long snapshotId,
                                           BigDecimal totalValue,
                                           BigDecimal dailyPnl,
                                           BigDecimal dailyPnlPercent) {
        return new PortfolioUpdatedEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                userSub,
                portfolioId,
                snapshotId,
                totalValue,
                dailyPnl,
                dailyPnlPercent
        );
    }

    @Override
    public String topic() {
        return KafkaTopics.PORTFOLIO_UPDATED;
    }

    @Override
    public String partitionKey() {
        return userSub;
    }
}
