package com.finance.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.user.dto.UserChartDrawingResponse;
import com.finance.user.model.UserChartDrawing;
import com.finance.user.repository.UserChartDrawingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserChartDrawingService {

    private static final TypeReference<List<Map<String, Object>>> DRAWING_LIST_TYPE =
            new TypeReference<>() {};

    private final UserChartDrawingRepository repository;
    private final TrackedAssetRepository trackedAssetRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UserChartDrawingResponse getOrDefault(String userSub, TrackedAssetType type, String code) {
        TrackedAsset tracked = resolveTracked(type, code);
        return repository.findByUserSubAndTrackedAsset_Id(userSub, tracked.getId())
                .map(d -> new UserChartDrawingResponse(toList(d.getDrawings()), d.getUpdatedAt()))
                .orElseGet(() -> new UserChartDrawingResponse(new ArrayList<>(), Instant.now()));
    }

    @Transactional
    public UserChartDrawingResponse upsert(String userSub, TrackedAssetType type, String code, List<Map<String, Object>> drawings) {
        TrackedAsset tracked = resolveTracked(type, code);
        UserChartDrawing entity = repository.findByUserSubAndTrackedAsset_Id(userSub, tracked.getId())
                .orElseGet(() -> UserChartDrawing.builder()
                        .userSub(userSub)
                        .trackedAsset(tracked)
                        .build());
        JsonNode node = drawings != null ? objectMapper.valueToTree(drawings) : JsonNodeFactory.instance.arrayNode();
        entity.setDrawings(node);
        UserChartDrawing saved = repository.save(entity);
        log.debug("Saved chart drawings userSub={} trackedAssetId={}", userSub, tracked.getId());
        return new UserChartDrawingResponse(toList(saved.getDrawings()), saved.getUpdatedAt());
    }

    private List<Map<String, Object>> toList(JsonNode node) {
        if (node == null || node.isNull()) return new ArrayList<>();
        return objectMapper.convertValue(node, DRAWING_LIST_TYPE);
    }

    private TrackedAsset resolveTracked(TrackedAssetType type, String code) {
        String normalized = type.normalizeCode(code);
        return trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalized)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tracked asset not found: " + type + " / " + normalized));
    }
}
