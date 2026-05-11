package com.finance.notification.user;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Log4j2
@Service
public class UserPreferenceCacheService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final Set<String> SUPPORTED_LOCALES = Set.of("tr", "en");
    private static final String QUERY = "SELECT language, theme, timezone FROM user_preferences WHERE user_sub = ?";
    private static final String BULK_QUERY = "SELECT user_sub, language, theme, timezone FROM user_preferences WHERE user_sub = ANY(?)";

    private final JdbcTemplate jdbcTemplate;
    private final Cache<String, PrefRow> cache;

    public UserPreferenceCacheService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(10_000)
                .build();
    }

    private record PrefRow(String language, String theme, String timezone) {}

    public void preload(Collection<String> userSubs) {
        Set<String> missing = new HashSet<>();
        for (String sub : userSubs) {
            if (sub == null || sub.isBlank()) continue;
            if (cache.getIfPresent(sub) == null) missing.add(sub);
        }
        if (missing.isEmpty()) return;
        jdbcTemplate.query(BULK_QUERY,
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", missing.toArray())),
                (rs, n) -> {
                    cache.put(rs.getString(1),
                            new PrefRow(rs.getString(2), rs.getString(3), rs.getString(4)));
                    return null;
                });
    }

    private Optional<PrefRow> load(String userSub) {
        if (userSub == null || userSub.isBlank()) return Optional.empty();
        PrefRow cached = cache.getIfPresent(userSub);
        if (cached != null) return Optional.of(cached);
        try {
            PrefRow row = jdbcTemplate.queryForObject(QUERY,
                    (rs, n) -> new PrefRow(rs.getString(1), rs.getString(2), rs.getString(3)),
                    userSub);
            if (row != null) cache.put(userSub, row);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public ZoneId resolveZone(String userSub) {
        return load(userSub).map(PrefRow::timezone)
                .map(UserPreferenceCacheService::parseZoneOrDefault)
                .orElse(DEFAULT_ZONE);
    }

    @Transactional(readOnly = true)
    public String resolveTheme(String userSub) {
        return load(userSub).map(PrefRow::theme)
                .map(UserPreferenceCacheService::canonicalTheme)
                .orElse("DARK");
    }

    @Transactional(readOnly = true)
    public Locale resolveLocale(String userSub) {
        return load(userSub).map(PrefRow::language)
                .map(UserPreferenceCacheService::canonicalLocale)
                .orElse(DEFAULT_LOCALE);
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
