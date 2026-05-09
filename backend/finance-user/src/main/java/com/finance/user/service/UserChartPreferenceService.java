package com.finance.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public UserChartPreferenceResponse upsert(String userSub, TrackedAssetType type, String code, Map<String, Object> config) {
        TrackedAsset tracked = resolveTracked(type, code);
        UserChartPreference entity = repository.findByUserSubAndTrackedAsset_Id(userSub, tracked.getId())
                .orElseGet(() -> UserChartPreference.builder()
                        .userSub(userSub)
                        .trackedAsset(tracked)
                        .build());
        JsonNode node = config != null ? objectMapper.valueToTree(config) : JsonNodeFactory.instance.objectNode();
        entity.setConfig(node);
        UserChartPreference saved = repository.save(entity);
        log.debug("Saved chart preferences userSub={} trackedAssetId={}", userSub, tracked.getId());
        return mapper.toResponse(saved);
    }

    private Map<String, Object> buildDefaults(TrackedAssetType type) {
        boolean isFund = type == TrackedAssetType.FUND;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("indicators", chartDefaults.defaults().indicators());
        map.put("fibTools", List.of());
        map.put("chartType", isFund ? chartDefaults.fund().chartType() : chartDefaults.defaults().chartType());
        map.put("showVolume", chartDefaults.defaults().showVolume());
        map.put("magnetMode", chartDefaults.defaults().magnetMode());
        map.put("iconSize", chartDefaults.defaults().iconSize());
        map.put("selectedIcon", chartDefaults.defaults().selectedIcon());
        if (isFund) {
            map.put("showInvestorCount", chartDefaults.fund().showInvestorCount());
            map.put("showPortfolioSize", chartDefaults.fund().showPortfolioSize());
        }
        return map;
    }

    private TrackedAsset resolveTracked(TrackedAssetType type, String code) {
        String normalized = type.normalizeCode(code);
        return trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalized)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tracked asset not found: " + type + " / " + normalized));
    }
}
