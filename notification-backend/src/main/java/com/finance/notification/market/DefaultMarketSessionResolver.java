package com.finance.notification.market;

import com.finance.notification.config.MarketSessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DefaultMarketSessionResolver implements MarketSessionResolver {

    private final MarketHoursProperties properties;
    private final MarketSessionProperties sessionProperties;

    @Override
    public Optional<MarketSession> resolve(SessionMarket market, Instant at) {
        return scheduleOf(market).map(schedule -> evaluate(schedule, at));
    }

    @Override
    public Optional<Instant> nextTransition(SessionMarket market, Instant at) {
        return scheduleOf(market).map(schedule -> findNextBoundary(schedule, at));
    }

    private Optional<MarketHoursProperties.MarketSchedule> scheduleOf(SessionMarket market) {
        if (market == null) return Optional.empty();
        return Optional.ofNullable(properties.markets().get(market));
    }

    private MarketSession evaluate(MarketHoursProperties.MarketSchedule schedule, Instant at) {
        ZonedDateTime now = at.atZone(schedule.zone());
        if (!schedule.tradingDays().contains(now.getDayOfWeek())) return MarketSession.CLOSED;
        if (schedule.open().equals(schedule.close())) return MarketSession.OPEN;
        LocalTime current = now.toLocalTime();
        boolean afterOpen = !current.isBefore(schedule.open());
        boolean beforeClose = current.isBefore(schedule.close());
        return (afterOpen && beforeClose) ? MarketSession.OPEN : MarketSession.CLOSED;
    }

    private Instant findNextBoundary(MarketHoursProperties.MarketSchedule schedule, Instant at) {
        if (schedule.open().equals(schedule.close())) return null;
        ZonedDateTime cursor = at.atZone(schedule.zone());
        for (int day = 0; day <= sessionProperties.lookaheadDays(); day++) {
            LocalDate date = cursor.toLocalDate().plusDays(day);
            if (!schedule.tradingDays().contains(date.getDayOfWeek())) continue;
            ZonedDateTime open = LocalDateTime.of(date, schedule.open()).atZone(schedule.zone());
            ZonedDateTime close = LocalDateTime.of(date, schedule.close()).atZone(schedule.zone());
            if (open.toInstant().isAfter(at)) return open.toInstant();
            if (close.toInstant().isAfter(at)) return close.toInstant();
        }
        return cursor.plusDays(sessionProperties.lookaheadDays()).toInstant();
    }
}
