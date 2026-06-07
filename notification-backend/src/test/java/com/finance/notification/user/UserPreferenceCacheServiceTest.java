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
    void resolveLocale_returnsEnglishFallback_whenSubBlank() {
        assertThat(service.resolveLocale("   ")).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void resolveLocale_returnsEnglish_whenRowNotFound() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("missing")))
                .thenThrow(new EmptyResultDataAccessException(1));

        Locale locale = service.resolveLocale("missing");

        assertThat(locale).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void userPreferenceSnapshot_defaults_returnsDarkEnglishIstanbul() {
        UserPreferenceCacheService.UserPreferenceSnapshot snap =
                UserPreferenceCacheService.UserPreferenceSnapshot.defaults();

        assertThat(snap.theme()).isEqualTo("DARK");
        assertThat(snap.locale()).isEqualTo(Locale.ENGLISH);
        assertThat(snap.zone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }

    @Test
    void resolveLocale_returnsTurkish_whenStoredLanguageTr() throws Exception {
        ResultSet rs = rs("tr", "DARK", "Europe/Istanbul");
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("user-1")))
                .thenAnswer(inv -> ((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0));

        Locale locale = service.resolveLocale("user-1");

        assertThat(locale.getLanguage()).isEqualTo("tr");
    }

    @Test
    void resolveLocale_returnsEnglish_whenStoredLanguageUnsupported() throws Exception {
        ResultSet rs = rs("fr", "DARK", "Europe/Istanbul");
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("user-1")))
                .thenAnswer(inv -> ((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0));

        Locale locale = service.resolveLocale("user-1");

        assertThat(locale).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void resolveLocale_returnsEnglish_whenLanguageBlank() throws Exception {
        ResultSet rs = rs("   ", "DARK", "Europe/Istanbul");
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq("user-1")))
                .thenAnswer(inv -> ((RowMapper<?>) inv.getArgument(1)).mapRow(rs, 0));

        Locale locale = service.resolveLocale("user-1");

        assertThat(locale).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void loadAll_bindsSubjectArrayToBulkQueryParameter_whenSetterExecutes() throws Exception {
        org.mockito.ArgumentCaptor<org.springframework.jdbc.core.PreparedStatementSetter> setterCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.jdbc.core.PreparedStatementSetter.class);
        org.mockito.Mockito.doReturn(null).when(jdbcTemplate).query(anyString(),
                setterCaptor.capture(), any(RowMapper.class));
        java.sql.PreparedStatement ps = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        java.sql.Connection connection = org.mockito.Mockito.mock(java.sql.Connection.class);
        java.sql.Array array = org.mockito.Mockito.mock(java.sql.Array.class);
        when(ps.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("text"), any(Object[].class))).thenReturn(array);

        service.loadAll(List.of("user-1"));
        setterCaptor.getValue().setValues(ps);

        org.mockito.Mockito.verify(ps).setArray(eq(1), eq(array));
    }

    @Test
    void loadAll_fallsBackToIstanbulZone_whenTimezoneBlank() throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("user-1");
        when(rs.getString(2)).thenReturn("en");
        when(rs.getString(3)).thenReturn("DARK");
        when(rs.getString(4)).thenReturn("   ");
        stubBulkQueryWithRow(rs);

        Map<String, UserPreferenceCacheService.UserPreferenceSnapshot> result =
                service.loadAll(List.of("user-1"));

        assertThat(result.get("user-1").zone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }

    @Test
    void loadAll_fallsBackToIstanbulZone_whenTimezoneInvalid() throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("user-1");
        when(rs.getString(2)).thenReturn("en");
        when(rs.getString(3)).thenReturn("DARK");
        when(rs.getString(4)).thenReturn("Not/AZone");
        stubBulkQueryWithRow(rs);

        Map<String, UserPreferenceCacheService.UserPreferenceSnapshot> result =
                service.loadAll(List.of("user-1"));

        assertThat(result.get("user-1").zone()).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }

    private void stubBulkQueryWithRow(ResultSet rs) {
        org.mockito.Mockito.doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(2);
            mapper.mapRow(rs, 0);
            return null;
        }).when(jdbcTemplate).query(anyString(),
                any(org.springframework.jdbc.core.PreparedStatementSetter.class),
                any(RowMapper.class));
    }

    @Test
    void loadAll_populatesResultMap_andFillsDefaultsForMissingSubs() throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("user-1");
        when(rs.getString(2)).thenReturn("tr");
        when(rs.getString(3)).thenReturn("LIGHT");
        when(rs.getString(4)).thenReturn("Europe/Istanbul");
        org.mockito.Mockito.doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(2);
            mapper.mapRow(rs, 0);
            return null;
        }).when(jdbcTemplate).query(anyString(),
                any(org.springframework.jdbc.core.PreparedStatementSetter.class),
                any(RowMapper.class));

        Map<String, UserPreferenceCacheService.UserPreferenceSnapshot> result =
                service.loadAll(List.of("user-1", "user-2"));

        assertThat(result.get("user-1").locale().getLanguage()).isEqualTo("tr");
        assertThat(result.get("user-1").theme()).isEqualTo("LIGHT");
        assertThat(result.get("user-2").locale()).isEqualTo(Locale.ENGLISH);
    }
}
