package com.finance.notification.broadcast.service;

import com.finance.notification.user.UserPreferenceCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserPreferenceCacheRecipientDirectory implements RecipientDirectory {

    private final UserPreferenceCacheRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Page<String> findUserSubs(Pageable pageable) {
        return repository.findAll(pageable).map(c -> c.getUserSub());
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }
}
