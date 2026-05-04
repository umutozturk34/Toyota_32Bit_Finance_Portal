package com.finance.backend.service;

import com.finance.backend.dto.UserPreferenceResponse;
import com.finance.backend.dto.UserPreferenceUpdateRequest;
import com.finance.backend.mapper.UserPreferenceMapper;
import com.finance.backend.model.UserPreference;
import com.finance.backend.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository repository;
    private final UserPreferenceMapper mapper;

    @Transactional(readOnly = true)
    public UserPreferenceResponse getOrDefault(String userSub) {
        return repository.findById(userSub)
                .map(mapper::toResponse)
                .orElseGet(() -> mapper.toResponse(UserPreference.defaultsFor(userSub)));
    }

    @Transactional
    public UserPreferenceResponse upsert(String userSub, UserPreferenceUpdateRequest request) {
        UserPreference entity = repository.findById(userSub)
                .orElseGet(() -> UserPreference.defaultsFor(userSub));
        applyUpdates(entity, request);
        return mapper.toResponse(repository.save(entity));
    }

    private void applyUpdates(UserPreference entity, UserPreferenceUpdateRequest request) {
        if (request.theme() != null) entity.setTheme(request.theme());
        if (request.language() != null) entity.setLanguage(request.language());
        if (request.timezone() != null) entity.setTimezone(request.timezone());
        if (request.defaultChartRange() != null) entity.setDefaultChartRange(request.defaultChartRange());
        if (request.reportFrequency() != null) entity.setReportFrequency(request.reportFrequency());
        if (request.onboardingCompleted() != null) entity.setOnboardingCompleted(request.onboardingCompleted());
    }
}
