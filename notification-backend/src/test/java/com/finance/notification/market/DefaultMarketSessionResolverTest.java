package com.finance.notification.market;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMarketSessionResolverTest {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");
    private static final Set<DayOfWeek> WEEKDAYS = Set.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private DefaultMarketSessionResolver resolverWith(MarketHoursProperties.MarketSchedule stockSchedule) {
        Map<SessionMarket, MarketHoursProperties.MarketSchedule> map = new EnumMap<>(SessionMarket.class);
        map.put(SessionMarket.STOCK, stockSchedule);
        return new DefaultMarketSessionResolver(new MarketHoursProperties(map));
    }

    private MarketHoursProperties.MarketSchedule stockHours() {
        return new MarketHoursProperties.MarketSchedule(
                LocalTime.of(10, 0), LocalTime.of(18, 0), WEEKDAYS, IST);
    }

    @Test
    void should_returnOpen_when_withinTradingHoursOnWeekday() {
        DefaultMarketSessionResolver resolver = resolverWith(stockHours());
        Instant tuesdayNoon = ZonedDateTime.of(2026, 5, 5, 12, 0, 0, 0, IST).toInstant();

        Optional<MarketSession> session = resolver.resolve(SessionMarket.STOCK, tuesdayNoon);

        assertThat(session).contains(MarketSession.OPEN);
    }

    @Test
    void should_returnClosed_when_beforeOpenOnWeekday() {
        DefaultMarketSessionResolver resolver = resolverWith(stockHours());
        Instant tuesdayMorning = ZonedDateTime.of(2026, 5, 5, 9, 30, 0, 0, IST).toInstant();

        Optional<MarketSession> session = resolver.resolve(SessionMarket.STOCK, tuesdayMorning);

        assertThat(session).contains(MarketSession.CLOSED);
    }

    @Test
    void should_returnClosed_when_atOrAfterCloseTime() {
        DefaultMarketSessionResolver resolver = resolverWith(stockHours());
        Instant tuesdayClose = ZonedDateTime.of(2026, 5, 5, 18, 0, 0, 0, IST).toInstant();

        Optional<MarketSession> session = resolver.resolve(SessionMarket.STOCK, tuesdayClose);

        assertThat(session).contains(MarketSession.CLOSED);
    }

    @Test
    void should_returnClosed_when_weekend() {
        DefaultMarketSessionResolver resolver = resolverWith(stockHours());
        Instant saturdayNoon = ZonedDateTime.of(2026, 5, 9, 12, 0, 0, 0, IST).toInstant();

        Optional<MarketSession> session = resolver.resolve(SessionMarket.STOCK, saturdayNoon);

        assertThat(session).contains(MarketSession.CLOSED);
    }

    @Test
    void should_returnEmpty_when_marketNotConfigured() {
        DefaultMarketSessionResolver resolver = resolverWith(stockHours());

        Optional<MarketSession> session = resolver.resolve(SessionMarket.FOREX, Instant.now());

        assertThat(session).isEmpty();
    }

    @Test
    void should_returnSameDayOpen_when_currentlyBeforeOpen() {
        DefaultMarketSessionResolver resolver = resolverWith(stockHours());
        Instant tuesdayEarly = ZonedDateTime.of(2026, 5, 5, 8, 0, 0, 0, IST).toInstant();

        Optional<Instant> next = resolver.nextTransition(SessionMarket.STOCK, tuesdayEarly);

        assertThat(next).contains(ZonedDateTime.of(2026, 5, 5, 10, 0, 0, 0, IST).toInstant());
    }

    @Test
    void should_returnSameDayClose_when_currentlyOpen() {
        DefaultMarketSessionResolver resolver = resolverWith(stockHours());
        Instant tuesdayMid = ZonedDateTime.of(2026, 5, 5, 12, 0, 0, 0, IST).toInstant();

        Optional<Instant> next = resolver.nextTransition(SessionMarket.STOCK, tuesdayMid);

        assertThat(next).contains(ZonedDateTime.of(2026, 5, 5, 18, 0, 0, 0, IST).toInstant());
    }

    @Test
    void should_skipWeekend_when_findingNextTransition() {
        DefaultMarketSessionResolver resolver = resolverWith(stockHours());
        Instant fridayEvening = ZonedDateTime.of(2026, 5, 8, 19, 0, 0, 0, IST).toInstant();

        Optional<Instant> next = resolver.nextTransition(SessionMarket.STOCK, fridayEvening);

        assertThat(next).contains(ZonedDateTime.of(2026, 5, 11, 10, 0, 0, 0, IST).toInstant());
    }
}
