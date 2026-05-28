package com.finance.common.cache;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-backed {@link AssetSnapshotCache} that reads market snapshots written under the
 * {@code market:<label>:snapshot:<code>} key scheme. Per-market JSON field names are taken from
 * {@link MarketType}, and the primary price field falls back to the secondary one when absent.
 * For VIOP entries the quote currency is derived from the symbol via
 * {@link Currency#viopQuoteCurrencyOf(String)} rather than the stored exchange-currency field,
 * which is not the FX quote currency. All read/parse failures are logged and degrade to an empty
 * result instead of propagating.
 */
@Log4j2
@Component
public class RedisAssetSnapshotCache implements AssetSnapshotCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisAssetSnapshotCache(RedisConnectionFactory connectionFactory) {
        this.redisTemplate = new StringRedisTemplate(connectionFactory);
        this.objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    @Override
    public Optional<AssetSnapshot> findByCode(MarketType type, String code) {
        if (code == null) return Optional.empty();
        try {
            String json = redisTemplate.opsForValue().get(buildKey(type, code));
            if (json == null) return Optional.empty();
            return parseSnapshot(type, json);
        } catch (Exception e) {
            log.warn("Instrument snapshot read failed type={} code={}: {}", type, code, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Map<String, AssetSnapshot> findByCodes(MarketType type, Set<String> codes) {
        if (codes == null || codes.isEmpty()) return Map.of();
        List<String> orderedCodes = codes.stream().toList();
        List<String> keys = orderedCodes.stream().map(c -> buildKey(type, c)).toList();
        Map<String, AssetSnapshot> result = new HashMap<>();
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) return result;
            for (int i = 0; i < orderedCodes.size(); i++) {
                String json = values.get(i);
                if (json == null) continue;
                String code = orderedCodes.get(i);
                parseSnapshot(type, json).ifPresent(s -> result.put(code, s));
            }
        } catch (Exception e) {
            log.warn("Instrument snapshot batch read failed type={} count={}: {}",
                    type, codes.size(), e.getMessage());
        }
        return result;
    }

    private Optional<AssetSnapshot> parseSnapshot(MarketType type, String json) {
        try {
            JsonNode root = unwrapTypeTagged(objectMapper.readTree(json));
            String code = textField(root, type.codeField());
            String name = textField(root, "name");
            String image = textField(root, "image");
            BigDecimal price = numericField(root, type.primaryPriceField());
            if (price == null && type.fallbackPriceField() != null) {
                price = numericField(root, type.fallbackPriceField());
            }
            BigDecimal changeAmount = numericField(root, "changeAmount");
            BigDecimal changePercent = numericField(root, "changePercent");
            if (code == null) return Optional.empty();
            String currency = type == MarketType.VIOP ? Currency.viopQuoteCurrencyOf(code).name() : null;
            if (currency == null || currency.isBlank()) currency = "TRY";
            return Optional.of(new AssetSnapshot(code, name, image, price, changeAmount, changePercent, currency));
        } catch (Exception e) {
            log.warn("Instrument snapshot parse failed type={}: {}", type, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Unwraps a Jackson default-typing envelope ({@code ["type", value]}) to the underlying value,
     * so snapshots serialized with polymorphic type tags can be read transparently.
     */
    private static JsonNode unwrapTypeTagged(JsonNode node) {
        if (node != null && node.isArray() && node.size() == 2 && node.get(0).isString()) {
            return node.get(1);
        }
        return node;
    }

    private static String textField(JsonNode root, String field) {
        if (root == null || field == null) return null;
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return null;
        return node.asString(null);
    }

    private static BigDecimal numericField(JsonNode root, String field) {
        if (root == null || field == null) return null;
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return null;
        if (node.isArray() && node.size() == 2) {
            node = node.get(1);
        }
        if (node.isNumber()) return node.decimalValue();
        if (node.isString()) {
            try {
                return new BigDecimal(node.asString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String buildKey(MarketType type, String code) {
        return "market:" + type.redisLabel() + ":snapshot:" + code;
    }
}
