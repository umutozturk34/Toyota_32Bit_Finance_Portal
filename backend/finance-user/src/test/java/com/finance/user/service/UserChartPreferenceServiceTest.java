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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserChartPreferenceServiceTest {

    private static final String USER = "user-1";
    private static final String CODE = "bitcoin";
    private static final TrackedAssetType TYPE = TrackedAssetType.CRYPTO;

    @Mock private UserChartPreferenceRepository repository;
    @Mock private TrackedAssetRepository trackedAssetRepository;
    @Mock private UserChartPreferenceMapper mapper;

    private UserChartPreferenceService service;

    @BeforeEach
    void setUp() {
        ChartDefaultsProperties defaults = new ChartDefaultsProperties(
                new ChartDefaultsProperties.Defaults(
                        List.of(new ChartDefaultsProperties.IndicatorDefault("SMA", 20, "#c084fc", false)),
                        "candle", false, "off", 22, "🚀"),
                new ChartDefaultsProperties.FundDefaults("line", false, false),
                new ChartDefaultsProperties.Limits(50, 8, 12),
                java.util.Map.of());
        service = new UserChartPreferenceService(repository, trackedAssetRepository, new ObjectMapper(), defaults, mapper);
    }

    @Test
    void getOrDefault_returnsConfiguredDefaults_whenNoRowExists() {
        TrackedAsset tracked = trackedAsset(42L);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.empty());

        UserChartPreferenceResponse response = service.getOrDefault(USER, TYPE, CODE);

        JsonNode config = response.config();
        assertThat(config.get("chartType").asString()).isEqualTo("candle");
        assertThat(config.get("showVolume").asBoolean()).isFalse();
        assertThat(config.get("magnetMode").asString()).isEqualTo("off");
        assertThat(config.get("iconSize").asInt()).isEqualTo(22);
        assertThat(config.get("indicators").isArray()).isTrue();
        assertThat(config.get("indicators").size()).isEqualTo(1);
    }

    @Test
    void getOrDefault_returnsStoredConfig_whenRowExists() {
        TrackedAsset tracked = trackedAsset(42L);
        ObjectNode storedNode = JsonNodeFactory.instance.objectNode();
        storedNode.put("indicators", "SMA");
        Instant updated = Instant.parse("2026-05-09T10:00:00Z");
        UserChartPreference entity = UserChartPreference.builder()
                .userSub(USER).trackedAsset(tracked).config(storedNode).updatedAt(updated).build();
        UserChartPreferenceResponse mapped = new UserChartPreferenceResponse(storedNode, updated);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        UserChartPreferenceResponse response = service.getOrDefault(USER, TYPE, CODE);

        assertThat(response).isSameAs(mapped);
    }

    @Test
    void getOrDefault_throws_whenTrackedAssetMissing() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrDefault(USER, TYPE, CODE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(CODE);
    }

    @Test
    void upsert_createsNewRow_whenNoExistingPreference() {
        TrackedAsset tracked = trackedAsset(42L);
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.put("rsi", true);
        UserChartPreferenceResponse mapped = new UserChartPreferenceResponse(config, Instant.now());
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.empty());
        when(repository.save(any(UserChartPreference.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(UserChartPreference.class))).thenReturn(mapped);

        UserChartPreferenceResponse response = service.upsert(USER, TYPE, CODE, config);

        ArgumentCaptor<UserChartPreference> captor = ArgumentCaptor.forClass(UserChartPreference.class);
        verify(repository).save(captor.capture());
        UserChartPreference saved = captor.getValue();
        assertThat(saved.getUserSub()).isEqualTo(USER);
        assertThat(saved.getTrackedAsset()).isSameAs(tracked);
        assertThat(saved.getConfig().get("rsi").asBoolean()).isTrue();
        assertThat(response).isSameAs(mapped);
    }

    @Test
    void upsert_updatesExistingRow_whenPreferenceFound() {
        TrackedAsset tracked = trackedAsset(42L);
        UserChartPreference existing = UserChartPreference.builder()
                .userSub(USER).trackedAsset(tracked)
                .config(JsonNodeFactory.instance.objectNode()).build();
        ObjectNode newConfig = JsonNodeFactory.instance.objectNode();
        newConfig.put("macd", true);
        UserChartPreferenceResponse mapped = new UserChartPreferenceResponse(newConfig, Instant.now());
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(mapped);

        UserChartPreferenceResponse response = service.upsert(USER, TYPE, CODE, newConfig);

        assertThat(existing.getConfig().get("macd").asBoolean()).isTrue();
        assertThat(response).isSameAs(mapped);
    }

    @Test
    void upsert_fallsBackToEmptyObject_whenConfigIsNull() {
        TrackedAsset tracked = trackedAsset(42L);
        UserChartPreferenceResponse mapped = new UserChartPreferenceResponse(JsonNodeFactory.instance.objectNode(), Instant.now());
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.empty());
        when(repository.save(any(UserChartPreference.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(UserChartPreference.class))).thenReturn(mapped);

        UserChartPreferenceResponse response = service.upsert(USER, TYPE, CODE, (JsonNode) null);

        ArgumentCaptor<UserChartPreference> captor = ArgumentCaptor.forClass(UserChartPreference.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getConfig().isEmpty()).isTrue();
        assertThat(response).isSameAs(mapped);
    }

    @Test
    void upsert_throws_whenTrackedAssetMissing() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(USER, TYPE, CODE, JsonNodeFactory.instance.objectNode()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    private TrackedAsset trackedAsset(Long id) {
        return TrackedAsset.builder().id(id).assetType(TYPE).assetCode(CODE).build();
    }
}
