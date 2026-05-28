package com.finance.market.fund.util;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** TEFAS date helpers: resolving the latest business day whose fund prices should be available. */
public final class TefasHelper {

    private TefasHelper() {}

    /**
     * Latest weekday on/before {@code from}, stepping back a day first if it is today and the
     * end-of-day cutover hour has not yet passed (prices not yet published).
     */
    public static LocalDate findLastBusinessDay(LocalDate from, ZoneId appZone, int eodCutoverHour) {
        var istanbulNow = ZonedDateTime.now(appZone);
        LocalDate date = from;
        if (date.equals(istanbulNow.toLocalDate()) && istanbulNow.getHour() < eodCutoverHour) {
            date = date.minusDays(1);
        }
        for (int i = 0; i < 5; i++) {
            var dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                return date;
            }
            date = date.minusDays(1);
        }
        return date;
    }
}
