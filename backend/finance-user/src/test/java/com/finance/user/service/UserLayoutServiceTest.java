package com.finance.user.service;


import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
        service = new UserLayoutService(repository, new UserLayoutMapperImpl(), java.util.List.of());
    }

    @Test
    void shouldReturnEmptyTransientLayout_whenNoDocumentExists() {
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());

        UserLayoutResponse layout = service.getOrEmpty(USER_SUB);

        assertThat(layout.userSub()).isEqualTo(USER_SUB);
        assertThat(layout.overview()).isNotNull();
        assertThat(layout.overview().isEmpty()).isTrue();
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

        assertThat(layout.overview().get("sections").get(0).get("id").asString()).isEqualTo("indices");
    }

    @Test
    void shouldUpsertNewDocument_whenSavingOverviewOnEmptyDb() throws Exception {
        JsonNode overview = objectMapper.readTree("""
                {"sections":[{"id":"watchlist","visible":true,"order":0}]}""");
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(UserLayout.class))).thenAnswer(inv -> inv.getArgument(0));

        UserLayoutResponse result = service.saveOverview(USER_SUB, overview);

        ArgumentCaptor<UserLayout> captor = ArgumentCaptor.forClass(UserLayout.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserSub()).isEqualTo(USER_SUB);
        assertThat(captor.getValue().getOverview().get("sections").get(0).get("id").asString()).isEqualTo("watchlist");
        assertThat(result.updatedAt()).isNotNull();
    }

    @Test
    void shouldOverrideExistingOverview_whenSavingNewLayout() throws Exception {
        JsonNode oldOverview = objectMapper.readTree("""
                {"sections":[{"id":"indices"}]}""");
        UserLayout existing = UserLayout.builder()
                .userSub(USER_SUB).overview(oldOverview).build();
        JsonNode newOverview = objectMapper.readTree("""
                {"sections":[{"id":"top-movers"},{"id":"news"}]}""");
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(UserLayout.class))).thenAnswer(inv -> inv.getArgument(0));

        UserLayoutResponse result = service.saveOverview(USER_SUB, newOverview);

        JsonNode resultSections = result.overview().get("sections");
        assertThat(resultSections.isArray()).isTrue();
        assertThat(resultSections.size()).isEqualTo(2);
        assertThat(resultSections.get(0).get("id").asString()).isEqualTo("top-movers");
    }
}
