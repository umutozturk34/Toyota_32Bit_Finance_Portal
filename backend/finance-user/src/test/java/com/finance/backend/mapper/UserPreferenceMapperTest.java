package com.finance.backend.mapper;

import com.finance.backend.dto.UserPreferenceResponse;
import com.finance.backend.dto.enums.ReportFrequency;
import com.finance.backend.dto.enums.ThemePreference;
import com.finance.backend.event.UserPreferencesUpdatedEvent;
import com.finance.backend.model.UserPreference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserPreferenceMapperTest {

    private static final String USER_SUB = "kc-user-sub";

    private final UserPreferenceMapper mapper = new UserPreferenceMapperImpl();

    @Test
    void should_returnAllFields_when_mappingEntityToResponse() {
        UserPreference entity = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(ThemePreference.LIGHT)
                .language("en")
                .timezone("UTC")
                .defaultChartRange("3M")
                .reportFrequency(ReportFrequency.WEEKLY)
                .onboardingCompleted(true)
                .build();

        UserPreferenceResponse response = mapper.toResponse(entity);

        assertThat(response.userSub()).isEqualTo(USER_SUB);
        assertThat(response.theme()).isEqualTo(ThemePreference.LIGHT);
        assertThat(response.language()).isEqualTo("en");
        assertThat(response.timezone()).isEqualTo("UTC");
        assertThat(response.defaultChartRange()).isEqualTo("3M");
        assertThat(response.reportFrequency()).isEqualTo(ReportFrequency.WEEKLY);
        assertThat(response.onboardingCompleted()).isTrue();
    }

    @Test
    void should_serializeEnumsAsNames_when_buildingUpdatedEvent() {
        UserPreference entity = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(ThemePreference.DARK)
                .language("tr")
                .timezone("Europe/Istanbul")
                .defaultChartRange("1M")
                .reportFrequency(ReportFrequency.DAILY)
                .onboardingCompleted(false)
                .build();

        UserPreferencesUpdatedEvent event = mapper.toUpdatedEvent(entity);

        assertThat(event.userSub()).isEqualTo(USER_SUB);
        assertThat(event.theme()).isEqualTo("DARK");
        assertThat(event.language()).isEqualTo("tr");
        assertThat(event.timezone()).isEqualTo("Europe/Istanbul");
        assertThat(event.defaultChartRange()).isEqualTo("1M");
        assertThat(event.reportFrequency()).isEqualTo("DAILY");
        assertThat(event.onboardingCompleted()).isFalse();
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void should_returnNullForEnumNames_when_enumsAreNull() {
        UserPreference entity = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(null)
                .reportFrequency(null)
                .build();

        UserPreferencesUpdatedEvent event = mapper.toUpdatedEvent(entity);

        assertThat(event.theme()).isNull();
        assertThat(event.reportFrequency()).isNull();
    }
}
