package com.finance.notification.alert.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.i18n.Translator;
import com.finance.common.model.MarketType;
import com.finance.notification.alert.dto.PriceAlertCreateRequest;
import com.finance.notification.alert.dto.PriceAlertResponse;
import com.finance.notification.alert.dto.PriceAlertUpdateRequest;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.alert.service.PriceAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertControllerTest {

    private static final String SUB = "user-1";

    @Mock private PriceAlertService service;
    @Mock private Translator translator;

    private PriceAlertController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new PriceAlertController(service, translator);
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(SUB)
                .claim("sub", SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PriceAlertResponse sample() {
        return new PriceAlertResponse(1L, MarketType.STOCK, "AAPL", "Apple", null,
                new BigDecimal("100"), new BigDecimal("1"), new BigDecimal("1"),
                AlertDirection.ABOVE, new BigDecimal("110"), "USD",
                new BigDecimal("100"), true, null, LocalDateTime.now());
    }

    @Test
    void create_delegatesToService_andWrapsResponse() {
        PriceAlertCreateRequest request = new PriceAlertCreateRequest(
                MarketType.STOCK, "AAPL", AlertDirection.ABOVE,
                new BigDecimal("110"), "USD", new BigDecimal("100"));
        when(service.create(SUB, request)).thenReturn(sample());

        ApiResponse<PriceAlertResponse> response = controller.create(jwt, request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().id()).isEqualTo(1L);
        verify(service).create(SUB, request);
    }

    @Test
    void list_returnsPagedResponse_withTotalCountFromService() {
        Page<PriceAlertResponse> page = new PageImpl<>(List.of(sample()),
                PageRequest.of(0, 20), 1);
        when(service.list(SUB, 0, 20)).thenReturn(page);

        ApiResponse<PagedResponse<PriceAlertResponse>> response = controller.list(jwt, 0, 20);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().content()).hasSize(1);
        assertThat(response.getData().totalElements()).isEqualTo(1L);
    }

    @Test
    void delete_invokesService_andReturnsVoidSuccess() {
        ApiResponse<Void> response = controller.delete(jwt, 5L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        verify(service).delete(5L, SUB);
    }

    @Test
    void reactivate_delegatesToService_andReturnsResponse() {
        when(service.reactivate(eq(7L), eq(SUB))).thenReturn(sample());

        ApiResponse<PriceAlertResponse> response = controller.reactivate(jwt, 7L);

        assertThat(response.isSuccess()).isTrue();
        verify(service).reactivate(7L, SUB);
    }

    @Test
    void update_delegatesToService_withRequest() {
        PriceAlertUpdateRequest request = new PriceAlertUpdateRequest(
                AlertDirection.BELOW, new BigDecimal("90"));
        when(service.update(8L, SUB, request)).thenReturn(sample());

        ApiResponse<PriceAlertResponse> response = controller.update(jwt, 8L, request);

        assertThat(response.isSuccess()).isTrue();
        verify(service).update(8L, SUB, request);
    }
}
