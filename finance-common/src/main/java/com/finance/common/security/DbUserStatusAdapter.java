package com.finance.common.security;

import com.finance.common.model.UserStatus;
import com.finance.common.repository.UserStatusRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class DbUserStatusAdapter implements UserStatusPort {

    private final UserStatusRepository repository;

    public DbUserStatusAdapter(UserStatusRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(String userSub) {
        if (userSub == null || userSub.isBlank()) return false;
        return repository.findById(userSub).map(UserStatus::isEnabled).orElse(true);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Boolean> activeStatusOf(Collection<String> userSubs) {
        Set<String> subs = new HashSet<>();
        for (String sub : userSubs) {
            if (sub != null && !sub.isBlank()) subs.add(sub);
        }
        if (subs.isEmpty()) return Map.of();
        Map<String, Boolean> result = new HashMap<>(subs.size());
        repository.findAllById(subs).forEach(row -> result.put(row.getUserSub(), row.isEnabled()));
        for (String sub : subs) {
            result.putIfAbsent(sub, true);
        }
        return result;
    }
}
