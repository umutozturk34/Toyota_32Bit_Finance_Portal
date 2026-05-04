package com.finance.notification.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPreferenceTest {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    @Test
    void defaultsFor_setsExpectedFlags() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("user-1");

        assertThat(prefs.getUserSub()).isEqualTo("user-1");
        assertThat(prefs.isInappPriceAlerts()).isTrue();
        assertThat(prefs.isEmailPriceAlerts()).isTrue();
        assertThat(prefs.isInappWatchlist()).isTrue();
        assertThat(prefs.isEmailWatchlist()).isFalse();
        assertThat(prefs.isInappReports()).isTrue();
        assertThat(prefs.isEmailReports()).isTrue();
        assertThat(prefs.isInappMessages()).isTrue();
        assertThat(prefs.isEmailMessages()).isFalse();
        assertThat(prefs.isInappSystem()).isTrue();
        assertThat(prefs.isEmailSystem()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "PRICE_ALERT_FIRED,true,true",
            "WATCHLIST_DELTA,false,true",
            "REPORT_READY,true,true",
            "MESSAGE,false,true",
            "SYSTEM,false,true"
    })
    void wantsEmailAndInApp_matchDefaultsPerType(NotificationType type, boolean expectedEmail, boolean expectedInApp) {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");

        assertThat(prefs.wantsEmail(type)).isEqualTo(expectedEmail);
        assertThat(prefs.wantsInApp(type)).isEqualTo(expectedInApp);
    }

    @Test
    void isInQuietHours_returnsFalseWhenWindowNotConfigured() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");

        boolean result = prefs.isInQuietHours(IST);

        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "08:00, 18:00, 09:00, true",
            "08:00, 18:00, 18:00, false",
            "08:00, 18:00, 07:59, false",
            "08:00, 18:00, 08:00, true"
    })
    void isInQuietHours_handlesSameDayWindow(LocalTime start, LocalTime end, LocalTime now, boolean expected) {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setQuietHoursStart(start);
        prefs.setQuietHoursEnd(end);

        boolean result = prefs.isInQuietHours(now);

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "22:00, 08:00, 23:00, true",
            "22:00, 08:00, 02:00, true",
            "22:00, 08:00, 07:59, true",
            "22:00, 08:00, 08:00, false",
            "22:00, 08:00, 12:00, false",
            "22:00, 08:00, 21:59, false",
            "22:00, 08:00, 22:00, true"
    })
    void isInQuietHours_handlesOvernightWindow(LocalTime start, LocalTime end, LocalTime now, boolean expected) {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setQuietHoursStart(start);
        prefs.setQuietHoursEnd(end);

        boolean result = prefs.isInQuietHours(now);

        assertThat(result).isEqualTo(expected);
    }
}
