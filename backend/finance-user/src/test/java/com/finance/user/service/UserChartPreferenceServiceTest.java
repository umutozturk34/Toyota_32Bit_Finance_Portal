package com.finance.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.user.config.ChartDefaultsProperties;
import com.finance.user.dto.UserChartPreferenceResponse;
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
import java.util.Map;
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

    private UserChartPreferenceService service;

    @BeforeEach
    void setUp() {
        ChartDefaultsProperties defaults = new ChartDefaultsProperties(
                new ChartDefaultsProperties.Defaults(
                        List.of(new ChartDefaultsProperties.IndicatorDefault("SMA", 20, "#c084fc", false)),
                        "candle", false, "off", 22, "🚀"),
                new ChartDefaultsProperties.FundDefaults("line", false, false));
        service = new UserChartPreferenceService(repository, trackedAssetRepository, new ObjectMapper(), defaults);
    }

    @Test
    void getOrDefault_returnsConfiguredDefaults_whenNoRowExists() {
        TrackedAsset tracked = trackedAsset(42L);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.empty());

        UserChartPreferenceResponse response = service.getOrDefault(USER, TYPE, CODE);

        Map<String, Object> config = response.config();
        assertThat(config).containsEntry("chartType", "candle")
                .containsEntry("showVolume", false)
                .containsEntry("magnetMode", "off")
                .containsEntry("iconSize", 22);
        assertThat(config.get("indicators")).asList().hasSize(1);
    }

    @Test
    void getOrDefault_returnsStoredConfig_whenRowExists() {
        TrackedAsset tracked = trackedAsset(42L);
        ObjectNode storedNode = JsonNodeFactory.instance.objectNode();
        storedNode.put("indicators", "SMA");
        Instant updated = Instant.parse("2026-05-09T10:00:00Z");
        UserChartPreference entity = UserChartPreference.builder()
                .userSub(USER).trackedAsset(tracked).config(storedNode).updatedAt(updated).build();
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.of(entity));

        UserChartPreferenceResponse response = service.getOrDefault(USER, TYPE, CODE);

        assertThat(response.config()).containsEntry("indicators", "SMA");
        assertThat(response.updatedAt()).isEqualTo(updated);
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
        Map<String, Object> config = Map.of("rsi", true);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.empty());
        when(repository.save(any(UserChartPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        UserChartPreferenceResponse response = service.upsert(USER, TYPE, CODE, config);

        ArgumentCaptor<UserChartPreference> captor = ArgumentCaptor.forClass(UserChartPreference.class);
        verify(repository).save(captor.capture());
        UserChartPreference saved = captor.getValue();
        assertThat(saved.getUserSub()).isEqualTo(USER);
        assertThat(saved.getTrackedAsset()).isSameAs(tracked);
        assertThat(saved.getConfig().get("rsi").asBoolean()).isTrue();
        assertThat(response.config()).containsEntry("rsi", true);
    }

    @Test
    void upsert_updatesExistingRow_whenPreferenceFound() {
        TrackedAsset tracked = trackedAsset(42L);
        UserChartPreference existing = UserChartPreference.builder()
                .userSub(USER).trackedAsset(tracked)
                .config(JsonNodeFactory.instance.objectNode()).build();
        Map<String, Object> newConfig = Map.of("macd", true);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        UserChartPreferenceResponse response = service.upsert(USER, TYPE, CODE, newConfig);

        assertThat(existing.getConfig().get("macd").asBoolean()).isTrue();
        assertThat(response.config()).containsEntry("macd", true);
    }

    @Test
    void upsert_fallsBackToEmptyObject_whenConfigIsNull() {
        TrackedAsset tracked = trackedAsset(42L);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.empty());
        when(repository.save(any(UserChartPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        UserChartPreferenceResponse response = service.upsert(USER, TYPE, CODE, null);

        Map<String, Object> storedConfig = response.config();
        assertThat(storedConfig).isEmpty();
    }

    @Test
    void upsert_throws_whenTrackedAssetMissing() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(USER, TYPE, CODE, Map.of()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    private TrackedAsset trackedAsset(Long id) {
        return TrackedAsset.builder().id(id).assetType(TYPE).assetCode(CODE).build();
    }
}
