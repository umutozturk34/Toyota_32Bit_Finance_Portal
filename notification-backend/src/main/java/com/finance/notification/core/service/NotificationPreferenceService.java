package com.finance.notification.core.service;

import com.finance.notification.core.dto.NotificationPreferenceResponse;
import com.finance.notification.core.dto.NotificationPreferenceUpdateRequest;
import com.finance.notification.core.mapper.NotificationPreferenceMapper;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;
    private final NotificationPreferenceMapper mapper;

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getOrDefault(String userSub) {
        return repository.findById(userSub)
                .map(mapper::toResponse)
                .orElseGet(() -> mapper.toResponse(NotificationPreference.defaultsFor(userSub)));
    }

    @Transactional
    public NotificationPreferenceResponse upsert(String userSub, NotificationPreferenceUpdateRequest request) {
        NotificationPreference preference = repository.findById(userSub)
                .orElseGet(() -> NotificationPreference.defaultsFor(userSub));
        preference.applyUpdate(request);
        NotificationPreference saved = repository.save(preference);
        log.info("Notification preferences upserted userSub={}", userSub);
        return mapper.toResponse(saved);
    }

    @Transactional
    public boolean ensureDefaultsExist(String userSub) {
        if (repository.existsById(userSub)) return false;
        repository.save(NotificationPreference.defaultsFor(userSub));
        log.info("Notification preferences seeded userSub={}", userSub);
        return true;
    }
}
