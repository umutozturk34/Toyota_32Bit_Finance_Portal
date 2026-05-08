package com.finance.user.service;
import com.finance.common.service.MarketSnapshotProcessor;

import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.mapper.UserLayoutMapperImpl;
import com.finance.user.model.UserLayout;
import com.finance.user.repository.UserLayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLayoutServiceTest {

    private static final String USER_SUB = "kc-user-uuid-456";

    @Mock private UserLayoutRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserLayoutService service;

    @BeforeEach
    void setUp() {
        service = new UserLayoutService(repository, new UserLayoutMapperImpl(), objectMapper, java.util.List.of());
    }

    @Test
    void shouldReturnEmptyTransientLayout_whenNoDocumentExists() {
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());

        UserLayoutResponse layout = service.getOrEmpty(USER_SUB);

        assertThat(layout.userSub()).isEqualTo(USER_SUB);
        assertThat(layout.overview()).isNotNull();
        assertThat(layout.overview()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void shouldReturnPersistedLayout_whenDocumentExists() throws Exception {
        JsonNode overview = objectMapper.readTree("""
                {"sections":[{"id":"indices","visible":true,"order":0}]}""");
        UserLayout persisted = UserLayout.builder()
                .userSub(USER_SUB).overview(overview).build();
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(persisted));

        UserLayoutResponse layout = service.getOrEmpty(USER_SUB);

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> sections = (java.util.List<Map<String, Object>>) layout.overview().get("sections");
        assertThat(sections.get(0).get("id")).isEqualTo("indices");
    }

    @Test
    void shouldUpsertNewDocument_whenSavingOverviewOnEmptyDb() throws Exception {
        Map<String, Object> overview = objectMapper.readValue(
                "{\"sections\":[{\"id\":\"watchlist\",\"visible\":true,\"order\":0}]}",
                new TypeReference<>() {});
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(UserLayout.class))).thenAnswer(inv -> inv.getArgument(0));

        UserLayoutResponse result = service.saveOverview(USER_SUB, overview);

        ArgumentCaptor<UserLayout> captor = ArgumentCaptor.forClass(UserLayout.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserSub()).isEqualTo(USER_SUB);
        assertThat(captor.getValue().getOverview().get("sections").get(0).get("id").asText()).isEqualTo("watchlist");
        assertThat(result.updatedAt()).isNotNull();
    }

    @Test
    void shouldOverrideExistingOverview_whenSavingNewLayout() throws Exception {
        JsonNode oldOverview = objectMapper.readTree("""
                {"sections":[{"id":"indices"}]}""");
        UserLayout existing = UserLayout.builder()
                .userSub(USER_SUB).overview(oldOverview).build();
        Map<String, Object> newOverview = objectMapper.readValue(
                "{\"sections\":[{\"id\":\"top-movers\"},{\"id\":\"news\"}]}",
                new TypeReference<>() {});
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(UserLayout.class))).thenAnswer(inv -> inv.getArgument(0));

        UserLayoutResponse result = service.saveOverview(USER_SUB, newOverview);

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> resultSections = (java.util.List<Map<String, Object>>) result.overview().get("sections");
        assertThat(resultSections).hasSize(2);
        assertThat(resultSections.get(0).get("id")).isEqualTo("top-movers");
    }
}
