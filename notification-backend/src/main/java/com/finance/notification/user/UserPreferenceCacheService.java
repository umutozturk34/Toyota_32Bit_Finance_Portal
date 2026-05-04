package com.finance.notification.user;

import com.finance.common.event.UserPreferencesUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferenceCacheService {

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
}
