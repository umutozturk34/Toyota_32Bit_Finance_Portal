package com.finance.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.user.dto.UserChartPreferenceResponse;
import com.finance.user.model.UserChartPreference;
import com.finance.user.repository.UserChartPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserChartPreferenceService {

    private final UserChartPreferenceRepository repository;
    private final TrackedAssetRepository trackedAssetRepository;

    @Transactional(readOnly = true)
    public UserChartPreferenceResponse getOrDefault(String userSub, TrackedAssetType type, String code) {
        TrackedAsset tracked = resolveTracked(type, code);
        return repository.findByUserSubAndTrackedAsset_Id(userSub, tracked.getId())
                .map(p -> new UserChartPreferenceResponse(p.getConfig(), p.getUpdatedAt()))
                .orElseGet(() -> new UserChartPreferenceResponse(JsonNodeFactory.instance.objectNode(), Instant.now()));
    }

    @Transactional
    public UserChartPreferenceResponse upsert(String userSub, TrackedAssetType type, String code, JsonNode config) {
        TrackedAsset tracked = resolveTracked(type, code);
        UserChartPreference entity = repository.findByUserSubAndTrackedAsset_Id(userSub, tracked.getId())
                .orElseGet(() -> UserChartPreference.builder()
                        .userSub(userSub)
                        .trackedAsset(tracked)
                        .build());
        entity.setConfig(config != null ? config : JsonNodeFactory.instance.objectNode());
        UserChartPreference saved = repository.save(entity);
        log.debug("Saved chart preferences userSub={} trackedAssetId={}", userSub, tracked.getId());
        return new UserChartPreferenceResponse(saved.getConfig(), saved.getUpdatedAt());
    }

    private TrackedAsset resolveTracked(TrackedAssetType type, String code) {
        String normalized = type.normalizeCode(code);
        return trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalized)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tracked asset not found: " + type + " / " + normalized));
    }
}
