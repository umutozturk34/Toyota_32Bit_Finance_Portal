package com.finance.market.core.cache;

import com.finance.common.model.MarketType;
import com.finance.market.core.dto.response.MarketAssetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopMoversRedisServiceTest {

    private static final String KEY = "market:topMovers";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOps;

    private ObjectMapper objectMapper;
    private TopMoversRedisService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        service = new TopMoversRedisService(redisTemplate, objectMapper);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
    }

    @Test
    void updateGainers_writesJsonUnderTypeGainersField() {
        List<MarketAssetResponse> payload = List.of(sample("AKBNK", new BigDecimal("100"), new BigDecimal("5")));

        service.updateGainers(MarketType.STOCK, payload);

        verify(hashOps).put(eq(KEY), eq("STOCK:GAINERS"), any(String.class));
    }

    @Test
    void updateLosers_writesJsonUnderTypeLosersField() {
        service.updateLosers(MarketType.CRYPTO, List.of());

        verify(hashOps).put(eq(KEY), eq("CRYPTO:LOSERS"), any(String.class));
    }

    @Test
    void updateIndices_writesUnderIndicesField() {
        service.updateIndices(List.of(sample("XU100", new BigDecimal("100"), new BigDecimal("0"))));

        verify(hashOps).put(eq(KEY), eq("INDICES"), any(String.class));
    }

    @Test
    void getGainers_returnsEmptyList_whenFieldMissing() {
        when(hashOps.get(KEY, "STOCK:GAINERS")).thenReturn(null);

        List<MarketAssetResponse> result = service.getGainers(MarketType.STOCK);

        assertThat(result).isEmpty();
    }

    @Test
    void getGainers_returnsDeserializedList_whenFieldPresent() throws Exception {
        MarketAssetResponse asset = sample("AKBNK", new BigDecimal("100"), new BigDecimal("5"));
        when(hashOps.get(KEY, "STOCK:GAINERS")).thenReturn(objectMapper.writeValueAsString(List.of(asset)));

        List<MarketAssetResponse> result = service.getGainers(MarketType.STOCK);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().code()).isEqualTo("AKBNK");
    }

    @Test
    void getLosers_returnsEmptyList_whenJsonInvalid() {
        when(hashOps.get(KEY, "STOCK:LOSERS")).thenReturn("not-a-json");

        List<MarketAssetResponse> result = service.getLosers(MarketType.STOCK);

        assertThat(result).isEmpty();
    }

    @Test
    void getIndices_returnsListUnderIndicesField() throws Exception {
        MarketAssetResponse idx = sample("XU100", new BigDecimal("100"), new BigDecimal("0"));
        when(hashOps.get(KEY, "INDICES")).thenReturn(objectMapper.writeValueAsString(List.of(idx)));

        List<MarketAssetResponse> result = service.getIndices();

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllGainers_groupsEntriesBySuffix() throws Exception {
        MarketAssetResponse a = sample("AKBNK", new BigDecimal("100"), new BigDecimal("5"));
        MarketAssetResponse c = sample("bitcoin", new BigDecimal("3000"), new BigDecimal("10"));
        when(hashOps.entries(KEY)).thenReturn(Map.of(
                "STOCK:GAINERS", objectMapper.writeValueAsString(List.of(a)),
                "CRYPTO:GAINERS", objectMapper.writeValueAsString(List.of(c)),
                "STOCK:LOSERS", objectMapper.writeValueAsString(List.of())
        ));

        Map<MarketType, List<MarketAssetResponse>> result = service.getAllGainers();

        assertThat(result).hasSize(2);
        assertThat(result.get(MarketType.STOCK)).hasSize(1);
        assertThat(result.get(MarketType.CRYPTO)).hasSize(1);
    }

    @Test
    void getAllLosers_returnsEmptyMap_whenNoEntries() {
        when(hashOps.entries(KEY)).thenReturn(Map.of());

        Map<MarketType, List<MarketAssetResponse>> result = service.getAllLosers();

        assertThat(result).isEmpty();
    }

    @Test
    void updateGainers_swallowsExceptionsAndDoesNotPropagate() {
        doThrow(new RuntimeException("redis down")).when(hashOps).put(any(), any(), any());

        service.updateGainers(MarketType.STOCK, List.of());

        verify(hashOps).put(eq(KEY), eq("STOCK:GAINERS"), any(String.class));
    }

    @Test
    void getAllGainers_returnsEmptyMap_whenInvalidEntryEncountered() {
        when(hashOps.entries(KEY)).thenReturn(Map.of("STOCK:GAINERS", "not-a-json"));

        Map<MarketType, List<MarketAssetResponse>> result = service.getAllGainers();

        assertThat(result).isEmpty();
    }

    @Test
    void getAllGainers_skipsFieldsWithoutMatchingSuffix() throws Exception {
        when(hashOps.entries(KEY)).thenReturn(Map.of(
                "STOCK:LOSERS", objectMapper.writeValueAsString(List.of()),
                "INDICES", objectMapper.writeValueAsString(List.of())
        ));

        Map<MarketType, List<MarketAssetResponse>> result = service.getAllGainers();

        assertThat(result).isEmpty();
        verify(hashOps, never()).get(any(), any());
    }

    private MarketAssetResponse sample(String code, BigDecimal price, BigDecimal change) {
        return new MarketAssetResponse(code, code, null, MarketType.STOCK,
                price, change, change, LocalDateTime.now(), null);
    }
}
