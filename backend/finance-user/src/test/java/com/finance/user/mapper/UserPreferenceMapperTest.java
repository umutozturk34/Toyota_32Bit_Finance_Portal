package com.finance.user.mapper;

import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.dto.enums.ThemePreference;
import com.finance.user.model.UserPreference;
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
                .onboardingCompleted(true)
                .build();

        UserPreferenceResponse response = mapper.toResponse(entity);

        assertThat(response.userSub()).isEqualTo(USER_SUB);
        assertThat(response.theme()).isEqualTo(ThemePreference.LIGHT);
        assertThat(response.language()).isEqualTo("en");
        assertThat(response.timezone()).isEqualTo("UTC");
        assertThat(response.defaultChartRange()).isEqualTo("3M");
        assertThat(response.onboardingCompleted()).isTrue();
    }
}
