package com.finance.notification.user;

import com.finance.common.event.UserPreferencesUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class UserPreferenceCacheService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

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
            return DEFAULT_ZONE;
        }
    }
}
