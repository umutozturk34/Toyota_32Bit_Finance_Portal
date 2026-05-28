package com.finance.user.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.user.config.ChartDefaultsProperties;
import com.finance.user.dto.UserChartPreferenceResponse;
import com.finance.user.mapper.UserChartPreferenceMapper;
import com.finance.user.model.UserChartPreference;
import com.finance.user.repository.UserChartPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Reads and upserts a user's chart preferences for a tracked asset. When none are saved it returns
 * config built from the configured defaults (with fund-specific overrides for {@code FUND} assets);
 * the asset is resolved from its (type, code) and must exist.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class UserChartPreferenceService {

    private final UserChartPreferenceRepository repository;
    private final TrackedAssetRepository trackedAssetRepository;
    private final ObjectMapper objectMapper;
    private final ChartDefaultsProperties chartDefaults;
    private final UserChartPreferenceMapper mapper;

    @Transactional(readOnly = true)
    public UserChartPreferenceResponse getOrDefault(String userSub, TrackedAssetType type, String code) {
        TrackedAsset tracked = resolveTracked(type, code);
        return repository.findByUserSubAndTrackedAsset_Id(userSub, tracked.getId())
                .map(mapper::toResponse)
                .orElseGet(() -> new UserChartPreferenceResponse(buildDefaults(type), Instant.now()));
    }

    @Transactional
    public UserChartPreferenceResponse upsert(String userSub, TrackedAssetType type, String code, JsonNode config) {
        TrackedAsset tracked = resolveTracked(type, code);
        UserChartPreference entity = repository.findByUserSubAndTrackedAsset_Id(userSub, tracked.getId())
                .orElseGet(() -> UserChartPreference.builder()
                        .userSub(userSub)
                        .trackedAsset(tracked)
                        .build());
        entity.setConfig(config != null && !config.isNull() ? config : JsonNodeFactory.instance.objectNode());
        UserChartPreference saved = repository.save(entity);
        log.debug("Saved chart preferences userSub={} trackedAssetId={}", userSub, tracked.getId());
        return mapper.toResponse(saved);
    }

    /** Assembles the default chart config for an asset type, applying fund-only fields when the type is {@code FUND}. */
    private JsonNode buildDefaults(TrackedAssetType type) {
        boolean isFund = type == TrackedAssetType.FUND;
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("indicators", objectMapper.valueToTree(chartDefaults.defaults().indicators()));
        node.set("fibTools", JsonNodeFactory.instance.arrayNode());
        node.put("chartType", isFund ? chartDefaults.fund().chartType() : chartDefaults.defaults().chartType());
        node.put("showVolume", chartDefaults.defaults().showVolume());
        node.put("magnetMode", chartDefaults.defaults().magnetMode());
        node.put("iconSize", chartDefaults.defaults().iconSize());
        node.put("selectedIcon", chartDefaults.defaults().selectedIcon());
        if (isFund) {
            node.put("showInvestorCount", chartDefaults.fund().showInvestorCount());
            node.put("showPortfolioSize", chartDefaults.fund().showPortfolioSize());
        }
        return node;
    }

    /** Resolves the tracked asset by normalized (type, code), throwing not-found if it is not tracked. */
    private TrackedAsset resolveTracked(TrackedAssetType type, String code) {
        String normalized = type.normalizeCode(code);
        return trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalized)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.trackedAsset.notFound", type, normalized));
    }
}
