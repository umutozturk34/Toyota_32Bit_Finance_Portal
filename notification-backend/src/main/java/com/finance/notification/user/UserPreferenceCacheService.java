package com.finance.notification.user;

import com.finance.common.event.UserPreferencesUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserPreferenceCacheService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final Set<String> SUPPORTED_LOCALES = Set.of("tr", "en");

    private final UserPreferenceCacheRepository repository;

    @Transactional
    public void upsertFromEvent(UserPreferencesUpdatedEvent event) {
        repository.findById(event.userSub())
                .ifPresentOrElse(
                        existing -> {
                            existing.applyEvent(event);
                            repository.save(existing);
                        },
                        () -> repository.save(UserPreferenceCache.fromEvent(event))
                );
        log.info("UserPreferenceCache upserted userSub={}", event.userSub());
    }

    @Transactional(readOnly = true)
    public ZoneId resolveZone(String userSub) {
        return repository.findById(userSub)
                .map(UserPreferenceCache::getTimezone)
                .map(UserPreferenceCacheService::parseZoneOrDefault)
                .orElse(DEFAULT_ZONE);
    }

    @Transactional(readOnly = true)
    public String resolveTheme(String userSub) {
        return repository.findById(userSub)
                .map(UserPreferenceCache::getTheme)
                .map(UserPreferenceCacheService::canonicalTheme)
                .orElse("DARK");
    }

    @Transactional(readOnly = true)
    public Locale resolveLocale(String userSub) {
        return repository.findById(userSub)
                .map(UserPreferenceCache::getLanguage)
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
