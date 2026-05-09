package com.finance.common.security;

import com.finance.common.model.UserStatus;
import com.finance.common.repository.UserStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DbUserStatusAdapter implements UserStatusPort {

    private final UserStatusRepository repository;

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(String userSub) {
        if (userSub == null || userSub.isBlank()) return false;
        return repository.findById(userSub).map(UserStatus::isEnabled).orElse(true);
    }
}
