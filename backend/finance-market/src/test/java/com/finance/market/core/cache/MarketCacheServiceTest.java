package com.finance.market.core.cache;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.core.model.BaseAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketCacheServiceTest {

    @SuppressWarnings("unchecked")
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @SuppressWarnings("unchecked")
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private ObjectMapper objectMapper;

    private MarketCacheService<TestAsset> service;
    private TestAsset stored;
    private boolean finderInvoked;

    static class TestAsset extends BaseAsset {
        @Override
        public java.math.BigDecimal getPriceTry() {
            return java.math.BigDecimal.ZERO;
        }

        @Override
        public String getCode() {
            return "TEST";
        }

        @Override
        public void scaleFields(int scale) {
            // no-op for the test stub
        }
    }

    @BeforeEach
    void setUp() {
        stored = new TestAsset();
        finderInvoked = false;
        service = new MarketCacheService<>(redisTemplate, objectMapper, "test:",
                Duration.ofMinutes(5), TestAsset.class, "TestAsset", code -> {
            finderInvoked = true;
            return code.equals("known") ? Optional.of(stored) : Optional.empty();
        });
    }

    @Test
    void getSnapshot_returnsCachedValue_whenRedisHit() {
        Object cached = new Object();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("test:abc")).thenReturn(cached);
        when(objectMapper.convertValue(cached, TestAsset.class)).thenReturn(stored);

        TestAsset result = service.getSnapshot("abc");

        assertThat(result).isSameAs(stored);
        assertThat(finderInvoked).isFalse();
    }

    @Test
    void getSnapshot_fallsBackToFinder_andCachesResult_whenRedisMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("test:known")).thenReturn(null);

        TestAsset result = service.getSnapshot("known");

        assertThat(result).isSameAs(stored);
        verify(valueOperations).set("test:known", stored, Duration.ofMinutes(5));
    }

    @Test
    void getSnapshot_raises_whenFinderEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("test:missing")).thenReturn(null);

        assertThatThrownBy(() -> service.getSnapshot("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void clearCache_deletesKey() {
        when(redisTemplate.delete("test:abc")).thenReturn(true);

        service.clearCache("abc");

        verify(redisTemplate).delete("test:abc");
    }

    @Test
    void clearCache_isQuiet_whenKeyAbsent() {
        when(redisTemplate.delete("test:abc")).thenReturn(false);

        service.clearCache("abc");

        verify(redisTemplate).delete("test:abc");
    }

    @Test
    void putSnapshot_writesEntityToRedis_withConfiguredTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.putSnapshot("abc", stored);

        verify(valueOperations).set("test:abc", stored, Duration.ofMinutes(5));
    }
}
