package com.finance.notification.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPreferenceTest {

    @Test
    void defaultsFor_setsExpectedFlags() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("user-1");

        assertThat(prefs.getUserSub()).isEqualTo("user-1");
        assertThat(prefs.isEmailEnabled()).isTrue();
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

    @Test
    void wantsEmail_returnsFalseForAllTypesWhenMasterSwitchOff() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("user-1");
        prefs.setEmailEnabled(false);

        for (NotificationType type : NotificationType.values()) {
            assertThat(prefs.wantsEmail(type)).as("type=%s", type).isFalse();
        }
    }

    @Test
    void wantsInApp_isUnaffectedByMasterEmailSwitch() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("user-1");
        prefs.setEmailEnabled(false);

        assertThat(prefs.wantsInApp(NotificationType.PRICE_ALERT_FIRED)).isTrue();
        assertThat(prefs.wantsInApp(NotificationType.MESSAGE)).isTrue();
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

}
