package com.finance.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.MarketType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class TopMoversRedisService {

    private static final String KEY = "market:topMovers";
    private static final String INDICES_FIELD = "INDICES";
    private static final TypeReference<List<MarketAssetResponse>> LIST_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void updateMovers(MarketType type, List<MarketAssetResponse> movers) {
        try {
            String json = objectMapper.writeValueAsString(movers);
            stringRedisTemplate.opsForHash().put(KEY, type.name(), json);
            log.debug("Updated top movers for {}: {} items", type, movers.size());
        } catch (Exception e) {
            log.warn("Failed to write top movers for {}: {}", type, e.getMessage());
        }
    }

    public void updateIndices(List<MarketAssetResponse> indices) {
        try {
            String json = objectMapper.writeValueAsString(indices);
            stringRedisTemplate.opsForHash().put(KEY, INDICES_FIELD, json);
            log.debug("Updated indices: {} items", indices.size());
        } catch (Exception e) {
            log.warn("Failed to write indices: {}", e.getMessage());
        }
    }

    public List<MarketAssetResponse> getIndices() {
        try {
            Object raw = stringRedisTemplate.opsForHash().get(KEY, INDICES_FIELD);
            if (raw == null) return List.of();
            return objectMapper.readValue(raw.toString(), LIST_TYPE);
        } catch (Exception e) {
            log.warn("Failed to read indices: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<MarketType, List<MarketAssetResponse>> getAllMovers() {
        Map<MarketType, List<MarketAssetResponse>> result = new EnumMap<>(MarketType.class);
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(KEY);
            entries.forEach((key, value) -> {
                String keyStr = key.toString();
                if (INDICES_FIELD.equals(keyStr)) return;
                try {
                    MarketType type = MarketType.valueOf(keyStr);
                    List<MarketAssetResponse> movers = objectMapper.readValue(value.toString(), LIST_TYPE);
                    result.put(type, movers);
                } catch (Exception e) {
                    log.warn("Failed to deserialize movers for {}: {}", keyStr, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Failed to read all movers: {}", e.getMessage());
        }
        return result;
    }
}
