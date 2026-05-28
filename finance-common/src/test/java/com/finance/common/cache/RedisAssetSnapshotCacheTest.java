package com.finance.common.cache;

import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisAssetSnapshotCacheTest {

    @Mock private RedisConnectionFactory connectionFactory;
    private final Map<String, String> store = new HashMap<>();
    private RedisAssetSnapshotCache cache;

    @BeforeEach
    void setUp() throws Exception {
        RedisConnection conn = mock(RedisConnection.class);
        lenient().when(connectionFactory.getConnection()).thenReturn(conn);
        cache = new RedisAssetSnapshotCache(connectionFactory);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0, String.class)));
        lenient().when(ops.multiGet(anyList())).thenAnswer(inv -> {
            List<String> keys = inv.getArgument(0);
            return keys.stream().map(store::get).toList();
        });

        Field field = RedisAssetSnapshotCache.class.getDeclaredField("redisTemplate");
        field.setAccessible(true);
        field.set(cache, template);
    }

    @Test
    void findByCode_returnsEmpty_whenCodeIsNull() {
        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, null);

        assertThat(result).isEmpty();
    }

    @Test
    void findByCode_returnsEmpty_whenRedisReturnsNull() {
        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isEmpty();
    }

    @Test
    void findByCode_parsesSnapshot_fromValidJsonValue() {
        store.put("market:crypto:snapshot:bitcoin",
                "{\"id\":\"bitcoin\",\"name\":\"Bitcoin\",\"image\":\"img\","
                        + "\"currentPriceTry\":1950000,\"changeAmount\":100,\"changePercent\":0.5}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("bitcoin");
        assertThat(result.get().priceTry()).isEqualByComparingTo("1950000");
    }

    @Test
    void findByCode_returnsEmpty_whenJsonMissingCodeField() {
        store.put("market:crypto:snapshot:bitcoin", "{\"name\":\"Bitcoin\"}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isEmpty();
    }

    @Test
    void findByCode_parsesWithFallbackPriceField_whenPrimaryMissing() {
        store.put("market:forex:snapshot:USD",
                "{\"currencyCode\":\"USD\",\"currentPrice\":32.5}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.FOREX, "USD");

        assertThat(result).isPresent();
        assertThat(result.get().priceTry()).isEqualByComparingTo("32.5");
    }

    @Test
    void findByCode_unwrapsTypeTaggedJson_whenWrappedAsTwoElementArray() {
        store.put("market:crypto:snapshot:bitcoin",
                "[\"com.finance.crypto.Snapshot\",{\"id\":\"bitcoin\",\"currentPriceTry\":50}]");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("bitcoin");
    }

    @Test
    void findByCode_returnsEmpty_whenJsonIsInvalid() {
        store.put("market:crypto:snapshot:bitcoin", "{invalid");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isEmpty();
    }

    @Test
    void findByCode_handlesNumericPriceAsStringField() {
        store.put("market:crypto:snapshot:bitcoin",
                "{\"id\":\"bitcoin\",\"currentPriceTry\":\"99.5\"}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isPresent();
        assertThat(result.get().priceTry()).isEqualByComparingTo("99.5");
    }

    @Test
    void findByCode_ignoresUnparseableNumericString() {
        store.put("market:crypto:snapshot:bitcoin",
                "{\"id\":\"bitcoin\",\"currentPriceTry\":\"not-a-number\"}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.CRYPTO, "bitcoin");

        assertThat(result).isPresent();
        assertThat(result.get().priceTry()).isNull();
    }

    @Test
    void findByCode_derivesViopCurrencyFromSymbol_ignoringStoredParaBirimi() {
        store.put("market:viop:snapshot:F_USDTRY0625",
                "{\"symbol\":\"F_USDTRY0625\",\"lastPrice\":32.5,\"currency\":\"USD\"}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.VIOP, "F_USDTRY0625");

        assertThat(result).isPresent();
        assertThat(result.get().currency()).isEqualTo("TRY");
    }

    @Test
    void findByCode_resolvesForeignViopCurrencyFromSymbol() {
        store.put("market:viop:snapshot:F_XAUUSD0625",
                "{\"symbol\":\"F_XAUUSD0625\",\"lastPrice\":2300,\"currency\":\"TRY\"}");

        Optional<AssetSnapshot> result = cache.findByCode(MarketType.VIOP, "F_XAUUSD0625");

        assertThat(result).isPresent();
        assertThat(result.get().currency()).isEqualTo("USD");
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
        store.put("market:crypto:snapshot:bitcoin",
                "{\"id\":\"bitcoin\",\"currentPriceTry\":1000}");
        store.put("market:crypto:snapshot:ethereum",
                "{\"id\":\"ethereum\",\"currentPriceTry\":50}");

        Map<String, AssetSnapshot> result = cache.findByCodes(MarketType.CRYPTO,
                Set.of("bitcoin", "ethereum", "missing"));

        assertThat(result).hasSize(2);
        assertThat(result).containsKeys("bitcoin", "ethereum");
    }

    @Test
    void findByCodes_returnsEmpty_whenMultiGetReturnsNull() throws Exception {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> nullOps = mock(ValueOperations.class);
        when(nullOps.multiGet(anyList())).thenReturn(null);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenReturn(nullOps);
        Field field = RedisAssetSnapshotCache.class.getDeclaredField("redisTemplate");
        field.setAccessible(true);
        field.set(cache, template);

        Map<String, AssetSnapshot> result = cache.findByCodes(MarketType.CRYPTO, Set.of("bitcoin"));

        assertThat(result).isEmpty();
    }
}
