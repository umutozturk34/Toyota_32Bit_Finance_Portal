package com.finance.common.security;

import com.finance.common.model.UserStatus;
import com.finance.common.repository.UserStatusRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
public class DbUserStatusAdapter implements UserStatusPort {

    private final UserStatusRepository repository;
    private final Cache<String, Boolean> cache;

    public DbUserStatusAdapter(UserStatusRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(10_000)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(String userSub) {
        if (userSub == null || userSub.isBlank()) return false;
        Boolean cached = cache.getIfPresent(userSub);
        if (cached != null) return cached;
        boolean enabled = repository.findById(userSub).map(UserStatus::isEnabled).orElse(true);
        cache.put(userSub, enabled);
        return enabled;
    }

    @Override
    @Transactional(readOnly = true)
    public void preload(Collection<String> userSubs) {
        Set<String> missing = new HashSet<>();
        for (String sub : userSubs) {
            if (sub == null || sub.isBlank()) continue;
            if (cache.getIfPresent(sub) == null) missing.add(sub);
        }
        if (missing.isEmpty()) return;
        repository.findAllById(missing).forEach(row -> cache.put(row.getUserSub(), row.isEnabled()));
        for (String sub : missing) {
            if (cache.getIfPresent(sub) == null) cache.put(sub, true);
        }
    }
}
