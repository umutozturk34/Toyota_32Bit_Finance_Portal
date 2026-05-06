package com.finance.notification.market;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties("notification.market-hours")
public record MarketHoursProperties(Map<SessionMarket, MarketSchedule> markets) {

    public MarketHoursProperties {
        markets = (markets == null) ? new EnumMap<>(SessionMarket.class) : markets;
    }

    public record MarketSchedule(
            LocalTime open,
            LocalTime close,
            Set<DayOfWeek> tradingDays,
            ZoneId zone) {
    }
}
