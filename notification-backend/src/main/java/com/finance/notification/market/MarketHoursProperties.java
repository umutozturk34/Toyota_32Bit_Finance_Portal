package com.finance.notification.market;

import com.finance.notification.market.session.SessionMarket;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-market trading schedules (open/close times, trading days and zone) that drive session
 * resolution. Markets without configuration are simply treated as having no schedule.
 */
@ConfigurationProperties("notification.market-hours")
public record MarketHoursProperties(Map<SessionMarket, MarketSchedule> markets) {

    public MarketHoursProperties {
        markets = (markets == null) ? new EnumMap<>(SessionMarket.class) : markets;
    }

    /** Trading window for a single market: daily open/close, active weekdays and exchange time zone. */
    public record MarketSchedule(
            LocalTime open,
            LocalTime close,
            Set<DayOfWeek> tradingDays,
            ZoneId zone) {
    }
}
