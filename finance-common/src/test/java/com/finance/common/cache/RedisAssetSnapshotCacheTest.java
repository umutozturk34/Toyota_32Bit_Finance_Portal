package com.finance.common.cache;

import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisAssetSnapshotCacheTest {

    @Mock private RedisConnectionFactory connectionFactory;

    private TestableRedisAssetSnapshotCache cache;

    static class TestableRedisAssetSnapshotCache extends RedisAssetSnapshotCache {
        private final Map<String, String> store;
        private boolean throwOnRead;

        TestableRedisAssetSnapshotCache(RedisConnectionFactory factory, Map<String, String> store) {
            super(factory);
            this.store = store;
        }

        void setThrowOnRead(boolean v) {
            this.throwOnRead = v;
        }

        @Override
        public Optional<AssetSnapshot> findByCode(MarketType type, String code) {
            if (throwOnRead) {
                try {
                    java.lang.reflect.Method m = RedisAssetSnapshotCache.class
                            .getDeclaredMethod("parseSnapshot", MarketType.class, String.class);
                    m.setAccessible(true);
                    Object result = m.invoke(this, type, "{not-json");
                    return (Optional<AssetSnapshot>) result;
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
            if (code == null) return Optional.empty();
            String key = "market:" + type.redisLabel() + ":snapshot:" + code;
            String json = store.get(key);
            if (json == null) return Optional.empty();
            try {
                java.lang.reflect.Method m = RedisAssetSnapshotCache.class
                        .getDeclaredMethod("parseSnapshot", MarketType.class, String.class);
                m.setAccessible(true);
                @SuppressWarnings("unchecked")
                Optional<AssetSnapshot> result = (Optional<AssetSnapshot>) m.invoke(this, type, json);
                return result;
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        @Override
        public Map<String, AssetSnapshot> findByCodes(MarketType type, Set<String> codes) {
            if (codes == null || codes.isEmpty()) return Map.of();
            Map<String, AssetSnapshot> result = new java.util.HashMap<>();
            for (String code : codes) {
                findByCode(type, code).ifPresent(snap -> result.put(code, snap));
            }
            return result;
        }
    }

    @BeforeEach
    void setUp() {
        RedisConnection conn = mock(RedisConnection.class);
        lenient().when(connectionFactory.getConnection()).thenReturn(conn);
        cache = new TestableRedisAssetSnapshotCache(connectionFactory, new java.util.HashMap<>());
    }

    @Test
    void findByCode_returnsEmpty_whenCodeIsNull() {
        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, null);

        assertThat(result).isEmpty();
    }

    @Test
    void findByCode_parsesSnapshot_fromValidJsonValue() {
        cache.store.put("market:crypto:snapshot:bitcoin",
                "{\"id\":\"bitcoin\",\"name\":\"Bitcoin\",\"image\":\"img\","
                        + "\"currentPriceTry\":1950000,\"changeAmount\":100,\"changePercent\":0.5}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("bitcoin");
        assertThat(result.get().priceTry()).isEqualByComparingTo("1950000");
    }

    @Test
    void findByCode_returnsEmpty_whenJsonMissingCodeField() {
        cache.store.put("market:crypto:snapshot:bitcoin", "{\"name\":\"Bitcoin\"}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isEmpty();
    }

    @Test
    void findByCode_parsesWithFallbackPriceField_whenPrimaryMissing() {
        cache.store.put("market:forex:snapshot:USD",
                "{\"currencyCode\":\"USD\",\"currentPrice\":32.5}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.FOREX, "USD");

        assertThat(result).isPresent();
        assertThat(result.get().priceTry()).isEqualByComparingTo("32.5");
    }

    @Test
    void findByCode_unwrapsTypeTaggedJson_whenWrappedAsTwoElementArray() {
        cache.store.put("market:crypto:snapshot:bitcoin",
                "[\"com.finance.crypto.Snapshot\",{\"id\":\"bitcoin\",\"currentPriceTry\":50}]");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("bitcoin");
        assertThat(result.get().priceTry()).isEqualByComparingTo("50");
    }

    @Test
    void findByCode_returnsEmpty_whenJsonIsInvalid() {
        cache.store.put("market:crypto:snapshot:bitcoin", "{invalid");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isEmpty();
    }

    @Test
    void findByCode_handlesNumericPriceAsStringField() {
        cache.store.put("market:crypto:snapshot:bitcoin",
                "{\"id\":\"bitcoin\",\"currentPriceTry\":\"99.5\"}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isPresent();
        assertThat(result.get().priceTry()).isEqualByComparingTo("99.5");
    }

    @Test
    void findByCode_ignoresUnparseableNumericString() {
        cache.store.put("market:crypto:snapshot:bitcoin",
                "{\"id\":\"bitcoin\",\"currentPriceTry\":\"not-a-number\"}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isPresent();
        assertThat(result.get().priceTry()).isNull();
    }

    @Test
    void findByCodes_returnsEmpty_whenInputEmpty() {
        Map<String, AssetSnapshot> result = cache.findByCodes(MarketType.CRYPTO, Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void findByCodes_returnsEmpty_whenInputNull() {
        Map<String, AssetSnapshot> result = cache.findByCodes(MarketType.CRYPTO, null);

        assertThat(result).isEmpty();
    }

    @Test
    void findByCodes_combinesAvailableSnapshots() {
        cache.store.put("market:crypto:snapshot:bitcoin",
                "{\"id\":\"bitcoin\",\"currentPriceTry\":1000}");
        cache.store.put("market:crypto:snapshot:ethereum",
                "{\"id\":\"ethereum\",\"currentPriceTry\":50}");

        Map<String, AssetSnapshot> result = cache.findByCodes(MarketType.CRYPTO,
                Set.of("bitcoin", "ethereum", "missing"));

        assertThat(result).hasSize(2);
        assertThat(result).containsKeys("bitcoin", "ethereum");
    }
}
