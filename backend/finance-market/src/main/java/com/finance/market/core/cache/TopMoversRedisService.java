package com.finance.market.core.cache;


import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.common.model.MarketType;
import com.finance.shared.util.RedisKeys;
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

    private static final String KEY = RedisKeys.TOP_MOVERS;
    private static final String INDICES_FIELD = RedisKeys.INDICES_FIELD;
    private static final String GAINERS_SUFFIX = RedisKeys.GAINERS_SUFFIX;
    private static final String LOSERS_SUFFIX = RedisKeys.LOSERS_SUFFIX;
    private static final TypeReference<List<MarketAssetResponse>> LIST_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void updateGainers(MarketType type, List<MarketAssetResponse> gainers) {
        writeField(gainersField(type), gainers, "gainers " + type);
    }

    public void updateLosers(MarketType type, List<MarketAssetResponse> losers) {
        writeField(losersField(type), losers, "losers " + type);
    }

    public void updateIndices(List<MarketAssetResponse> indices) {
        writeField(INDICES_FIELD, indices, "indices");
    }

    public List<MarketAssetResponse> getGainers(MarketType type) {
        return readField(gainersField(type), "gainers " + type);
    }

    public List<MarketAssetResponse> getLosers(MarketType type) {
        return readField(losersField(type), "losers " + type);
    }

    public List<MarketAssetResponse> getIndices() {
        return readField(INDICES_FIELD, "indices");
    }

    public Map<MarketType, List<MarketAssetResponse>> getAllGainers() {
        return readAllByType(GAINERS_SUFFIX);
    }

    public Map<MarketType, List<MarketAssetResponse>> getAllLosers() {
        return readAllByType(LOSERS_SUFFIX);
    }

    private void writeField(String field, List<MarketAssetResponse> payload, String label) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            stringRedisTemplate.opsForHash().put(KEY, field, json);
            log.debug("Updated {}: {} items", label, payload.size());
        } catch (Exception e) {
            log.warn("Failed to write {}: {}", label, e.getMessage());
        }
    }

    private List<MarketAssetResponse> readField(String field, String label) {
        try {
            Object raw = stringRedisTemplate.opsForHash().get(KEY, field);
            if (raw == null) return List.of();
            return objectMapper.readValue(raw.toString(), LIST_TYPE);
        } catch (Exception e) {
            log.warn("Failed to read {}: {}", label, e.getMessage());
            return List.of();
        }
    }

    private Map<MarketType, List<MarketAssetResponse>> readAllByType(String suffix) {
        Map<MarketType, List<MarketAssetResponse>> result = new EnumMap<>(MarketType.class);
        try {
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(KEY);
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                String field = entry.getKey().toString();
                if (!field.endsWith(suffix)) continue;
                String typeName = field.substring(0, field.length() - suffix.length());
                MarketType type = MarketType.valueOf(typeName);
                List<MarketAssetResponse> values = objectMapper.readValue(entry.getValue().toString(), LIST_TYPE);
                result.put(type, values);
            }
        } catch (Exception e) {
            log.warn("Failed to read {} entries: {}", suffix, e.getMessage());
        }
        return result;
    }

    private static String gainersField(MarketType type) {
        return type.name() + GAINERS_SUFFIX;
    }

    private static String losersField(MarketType type) {
        return type.name() + LOSERS_SUFFIX;
    }
}
