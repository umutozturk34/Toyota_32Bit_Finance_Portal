package com.finance.notification.user;

import lombok.extern.log4j.Log4j2;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads users' locale/theme/timezone from the replicated {@code user_preferences} table to localize
 * and theme outbound notifications. Values are canonicalized to supported locales/themes and a valid
 * zone, falling back to defaults (English, DARK, Europe/Istanbul) for missing or invalid data.
 */
@Log4j2
@Service
public class UserPreferenceCacheService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final Set<String> SUPPORTED_LOCALES = Set.of("tr", "en");
    private static final String QUERY = "SELECT language, theme, timezone FROM user_preferences WHERE user_sub = ?";
    private static final String BULK_QUERY = "SELECT user_sub, language, theme, timezone FROM user_preferences WHERE user_sub = ANY(?)";

    private final JdbcTemplate jdbcTemplate;

    public UserPreferenceCacheService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** A user's resolved presentation preferences (locale, theme, zone) with built-in defaults. */
    public record UserPreferenceSnapshot(Locale locale, String theme, ZoneId zone) {
        public static UserPreferenceSnapshot defaults() {
            return new UserPreferenceSnapshot(DEFAULT_LOCALE, "DARK", DEFAULT_ZONE);
        }
    }

    /** Bulk-loads preferences for the given subjects; every requested sub maps to a value (defaults if absent). */
    @Transactional(readOnly = true)
    public Map<String, UserPreferenceSnapshot> loadAll(Collection<String> userSubs) {
        Set<String> subs = new HashSet<>();
        for (String sub : userSubs) {
            if (sub != null && !sub.isBlank()) subs.add(sub);
        }
        if (subs.isEmpty()) return Map.of();
        Map<String, UserPreferenceSnapshot> result = new HashMap<>(subs.size());
        jdbcTemplate.query(BULK_QUERY,
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", subs.toArray())),
                (rs, n) -> {
                    result.put(rs.getString(1), toSnapshot(rs.getString(2), rs.getString(3), rs.getString(4)));
                    return null;
                });
        for (String sub : subs) {
            result.putIfAbsent(sub, UserPreferenceSnapshot.defaults());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Locale resolveLocale(String userSub) {
        return loadSingle(userSub).map(UserPreferenceSnapshot::locale).orElse(DEFAULT_LOCALE);
    }

    private Optional<UserPreferenceSnapshot> loadSingle(String userSub) {
        if (userSub == null || userSub.isBlank()) return Optional.empty();
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(QUERY,
                    (rs, n) -> toSnapshot(rs.getString(1), rs.getString(2), rs.getString(3)),
                    userSub));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static UserPreferenceSnapshot toSnapshot(String language, String theme, String timezone) {
        return new UserPreferenceSnapshot(canonicalLocale(language), canonicalTheme(theme), parseZoneOrDefault(timezone));
    }

    private static Locale canonicalLocale(String value) {
        if (value == null || value.isBlank()) return DEFAULT_LOCALE;
        String lower = value.toLowerCase(Locale.ROOT);
        return SUPPORTED_LOCALES.contains(lower) ? Locale.forLanguageTag(lower) : DEFAULT_LOCALE;
    }

    private static String canonicalTheme(String value) {
        if (value == null || value.isBlank()) return "DARK";
        String upper = value.toUpperCase();
        return "LIGHT".equals(upper) ? "LIGHT" : "DARK";
    }

    private static ZoneId parseZoneOrDefault(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(zoneId);
        } catch (DateTimeException ex) {
            log.warn("Invalid timezone in user preferences zoneId={} fallingBackTo={}", zoneId, DEFAULT_ZONE);
            return DEFAULT_ZONE;
        }
    }
}
