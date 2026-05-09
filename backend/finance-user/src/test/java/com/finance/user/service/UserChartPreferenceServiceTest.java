package com.finance.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
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
        service = new UserChartPreferenceService(repository, trackedAssetRepository);
    }

    @Test
    void getOrDefault_returnsEmptyConfig_whenNoRowExists() {
        TrackedAsset tracked = trackedAsset(42L);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.empty());

        UserChartPreferenceResponse response = service.getOrDefault(USER, TYPE, CODE);

        assertThat(response.config()).isEqualTo(JsonNodeFactory.instance.objectNode());
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void getOrDefault_returnsStoredConfig_whenRowExists() {
        TrackedAsset tracked = trackedAsset(42L);
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.put("indicators", "SMA");
        Instant updated = Instant.parse("2026-05-09T10:00:00Z");
        UserChartPreference entity = UserChartPreference.builder()
                .userSub(USER).trackedAsset(tracked).config(config).updatedAt(updated).build();
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.of(entity));

        UserChartPreferenceResponse response = service.getOrDefault(USER, TYPE, CODE);

        assertThat(response.config()).isSameAs(config);
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
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.put("rsi", true);
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
        assertThat(saved.getConfig()).isSameAs(config);
        assertThat(response.config()).isSameAs(config);
    }

    @Test
    void upsert_updatesExistingRow_whenPreferenceFound() {
        TrackedAsset tracked = trackedAsset(42L);
        UserChartPreference existing = UserChartPreference.builder()
                .userSub(USER).trackedAsset(tracked)
                .config(JsonNodeFactory.instance.objectNode()).build();
        ObjectNode newConfig = JsonNodeFactory.instance.objectNode();
        newConfig.put("macd", true);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        UserChartPreferenceResponse response = service.upsert(USER, TYPE, CODE, newConfig);

        assertThat(existing.getConfig()).isSameAs(newConfig);
        assertThat(response.config()).isSameAs(newConfig);
    }

    @Test
    void upsert_fallsBackToEmptyObject_whenConfigIsNull() {
        TrackedAsset tracked = trackedAsset(42L);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 42L)).thenReturn(Optional.empty());
        when(repository.save(any(UserChartPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        UserChartPreferenceResponse response = service.upsert(USER, TYPE, CODE, null);

        JsonNode storedConfig = response.config();
        assertThat(storedConfig).isEqualTo(JsonNodeFactory.instance.objectNode());
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
