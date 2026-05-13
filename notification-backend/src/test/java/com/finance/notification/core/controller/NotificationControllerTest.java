package com.finance.notification.core.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.i18n.Translator;
import com.finance.notification.core.dto.NotificationResponse;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.core.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final String SUB = "user-1";

    @Mock private NotificationService service;
    @Mock private Translator translator;

    private NotificationController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(service, translator);
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(SUB)
                .claim("sub", SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private NotificationResponse sample() {
        return new NotificationResponse(1L, NotificationType.SYSTEM, "title", "body",
                Map.of(), null, null, LocalDateTime.now());
    }

    @Test
    void list_returnsPagedResponse_fromService() {
        Page<NotificationResponse> page = new PageImpl<>(List.of(sample()),
                PageRequest.of(0, 20), 1);
        when(service.list(SUB, 0, 20, false, null)).thenReturn(page);

        ApiResponse<PagedResponse<NotificationResponse>> response =
                controller.list(jwt, false, 0, 20, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().content()).hasSize(1);
        assertThat(response.getData().totalElements()).isEqualTo(1L);
    }

    @Test
    void list_passesUnreadOnlyAndSearch_toService() {
        Page<NotificationResponse> page = new PageImpl<>(List.of(),
                PageRequest.of(0, 20), 0);
        when(service.list(SUB, 0, 20, true, "kw")).thenReturn(page);

        controller.list(jwt, true, 0, 20, "kw");

        verify(service).list(SUB, 0, 20, true, "kw");
    }

    @Test
    void unreadCount_returnsCountFromService() {
        when(service.unreadCount(SUB)).thenReturn(7L);

        ApiResponse<Long> response = controller.unreadCount(jwt);

        assertThat(response.getData()).isEqualTo(7L);
    }

    @Test
    void markRead_delegatesToService_andWrapsResponse() {
        when(service.markRead(3L, SUB)).thenReturn(sample());

        ApiResponse<NotificationResponse> response = controller.markRead(jwt, 3L);

        assertThat(response.isSuccess()).isTrue();
        verify(service).markRead(3L, SUB);
    }

    @Test
    void markAllRead_returnsAffectedCount_fromService() {
        when(service.markAllRead(SUB)).thenReturn(12);

        ApiResponse<Integer> response = controller.markAllRead(jwt);

        assertThat(response.getData()).isEqualTo(12);
    }

    @Test
    void delete_invokesService_andReturnsVoidSuccess() {
        ApiResponse<Void> response = controller.delete(jwt, 5L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        verify(service).delete(5L, SUB);
    }

    @Test
    void deleteAll_returnsRemovedCount_fromService() {
        when(service.deleteAll(SUB)).thenReturn(4);

        ApiResponse<Integer> response = controller.deleteAll(jwt);

        assertThat(response.getData()).isEqualTo(4);
    }
}
