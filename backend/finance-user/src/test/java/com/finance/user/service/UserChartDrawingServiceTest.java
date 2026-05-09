package com.finance.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.user.dto.UserChartDrawingResponse;
import com.finance.user.mapper.UserChartDrawingMapper;
import com.finance.user.model.UserChartDrawing;
import com.finance.user.repository.UserChartDrawingRepository;
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
class UserChartDrawingServiceTest {

    private static final String USER = "user-7";
    private static final String CODE = "ethereum";
    private static final TrackedAssetType TYPE = TrackedAssetType.CRYPTO;

    @Mock private UserChartDrawingRepository repository;
    @Mock private TrackedAssetRepository trackedAssetRepository;
    @Mock private UserChartDrawingMapper mapper;

    private UserChartDrawingService service;

    @BeforeEach
    void setUp() {
        service = new UserChartDrawingService(repository, trackedAssetRepository, new ObjectMapper(), mapper);
    }

    @Test
    void getOrDefault_returnsEmptyArray_whenNoRowExists() {
        TrackedAsset tracked = trackedAsset(11L);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 11L)).thenReturn(Optional.empty());

        UserChartDrawingResponse response = service.getOrDefault(USER, TYPE, CODE);

        assertThat(response.drawings()).isEmpty();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void getOrDefault_returnsStoredDrawings_whenRowExists() {
        TrackedAsset tracked = trackedAsset(11L);
        ArrayNode storedNode = JsonNodeFactory.instance.arrayNode();
        storedNode.addObject().put("type", "trendline");
        Instant updated = Instant.parse("2026-05-09T11:00:00Z");
        UserChartDrawing entity = UserChartDrawing.builder()
                .userSub(USER).trackedAsset(tracked).drawings(storedNode).updatedAt(updated).build();
        UserChartDrawingResponse mapped = new UserChartDrawingResponse(List.of(Map.of("type", "trendline")), updated);
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 11L)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(mapped);

        UserChartDrawingResponse response = service.getOrDefault(USER, TYPE, CODE);

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
    void upsert_createsNewRow_whenNoExistingDrawing() {
        TrackedAsset tracked = trackedAsset(11L);
        List<Map<String, Object>> drawings = List.of(Map.of("type", "fibonacci"));
        UserChartDrawingResponse mapped = new UserChartDrawingResponse(drawings, Instant.now());
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 11L)).thenReturn(Optional.empty());
        when(repository.save(any(UserChartDrawing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(UserChartDrawing.class))).thenReturn(mapped);

        UserChartDrawingResponse response = service.upsert(USER, TYPE, CODE, drawings);

        ArgumentCaptor<UserChartDrawing> captor = ArgumentCaptor.forClass(UserChartDrawing.class);
        verify(repository).save(captor.capture());
        UserChartDrawing saved = captor.getValue();
        assertThat(saved.getUserSub()).isEqualTo(USER);
        assertThat(saved.getTrackedAsset()).isSameAs(tracked);
        assertThat(saved.getDrawings().get(0).get("type").asText()).isEqualTo("fibonacci");
        assertThat(response).isSameAs(mapped);
    }

    @Test
    void upsert_updatesExistingRow_whenDrawingFound() {
        TrackedAsset tracked = trackedAsset(11L);
        UserChartDrawing existing = UserChartDrawing.builder()
                .userSub(USER).trackedAsset(tracked)
                .drawings(JsonNodeFactory.instance.arrayNode()).build();
        List<Map<String, Object>> newDrawings = List.of(Map.of("type", "rectangle"));
        UserChartDrawingResponse mapped = new UserChartDrawingResponse(newDrawings, Instant.now());
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 11L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(mapped);

        UserChartDrawingResponse response = service.upsert(USER, TYPE, CODE, newDrawings);

        assertThat(existing.getDrawings().get(0).get("type").asText()).isEqualTo("rectangle");
        assertThat(response).isSameAs(mapped);
    }

    @Test
    void upsert_fallsBackToEmptyArray_whenDrawingsIsNull() {
        TrackedAsset tracked = trackedAsset(11L);
        UserChartDrawingResponse mapped = new UserChartDrawingResponse(List.of(), Instant.now());
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.of(tracked));
        when(repository.findByUserSubAndTrackedAsset_Id(USER, 11L)).thenReturn(Optional.empty());
        when(repository.save(any(UserChartDrawing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(UserChartDrawing.class))).thenReturn(mapped);

        UserChartDrawingResponse response = service.upsert(USER, TYPE, CODE, null);

        ArgumentCaptor<UserChartDrawing> captor = ArgumentCaptor.forClass(UserChartDrawing.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDrawings().isEmpty()).isTrue();
        assertThat(response).isSameAs(mapped);
    }

    @Test
    void upsert_throws_whenTrackedAssetMissing() {
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TYPE, CODE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(USER, TYPE, CODE, List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    private TrackedAsset trackedAsset(Long id) {
        return TrackedAsset.builder().id(id).assetType(TYPE).assetCode(CODE).build();
    }
}
