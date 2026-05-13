package com.finance.notification.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceCacheServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private UserPreferenceCacheService service;

    @BeforeEach
    void setUp() {
        service = new UserPreferenceCacheService(jdbcTemplate);
    }

    private ResultSet rs(String lang, String theme, String tz) throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn(lang);
        when(rs.getString(2)).thenReturn(theme);
        when(rs.getString(3)).thenReturn(tz);
        return rs;
    }

    @Test
    void loadAll_returnsEmpty_whenInputEmpty() {
        Map<String, UserPreferenceCacheService.UserPreferenceSnapshot> result =
                service.loadAll(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void loadAll_ignoresNullAndBlankSubs() {
        Map<String, UserPreferenceCacheService.UserPreferenceSnapshot> result =
                service.loadAll(java.util.Arrays.asList(null, "  ", "   "));

        assertThat(result).isEmpty();
    }

    @Test
    void resolveZone_returnsDefault_whenUserSubBlank() {
        ZoneId zone = service.resolveZone("   ");

        assertThat(zone).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }

    @Test
    void resolveZone_returnsDefault_whenRowNotFound() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("missing")))
                .thenThrow(new EmptyResultDataAccessException(1));

        ZoneId zone = service.resolveZone("missing");

        assertThat(zone).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }

    @Test
    void resolveTheme_returnsDarkFallback_whenSubBlank() {
        assertThat(service.resolveTheme(null)).isEqualTo("DARK");
    }

    @Test
    void resolveLocale_returnsEnglishFallback_whenSubBlank() {
        assertThat(service.resolveLocale("   ")).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void userPreferenceSnapshot_defaults_returnsDarkEnglishIstanbul() {
        UserPreferenceCacheService.UserPreferenceSnapshot snap =
                UserPreferenceCacheService.UserPreferenceSnapshot.defaults();

        assertThat(snap.theme()).isEqualTo("DARK");
        assertThat(snap.locale()).isEqualTo(Locale.ENGLISH);
        assertThat(snap.zone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }
}
