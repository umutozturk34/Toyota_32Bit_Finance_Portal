package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.derivative.dto.request.CloseDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.OpenDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.UpdateDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.response.DerivativePositionResponse;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.service.DerivativePositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DerivativePositionControllerTest {

    private static final Long PORTFOLIO_ID = 1L;
    private static final Long POSITION_ID = 100L;
    private static final String USER_SUB = "user-1";

    @Mock private DerivativePositionService service;
    @Mock private Translator translator;

    private DerivativePositionController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new DerivativePositionController(service, translator);
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER_SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", USER_SUB)
                .build();
    }

    @Test
    void should_returnAllPositions_when_listInvokedWithoutOpenOnly() {
        List<DerivativePositionResponse> data = List.of();
        when(service.list(PORTFOLIO_ID, USER_SUB)).thenReturn(data);

        ApiResponse<List<DerivativePositionResponse>> response =
                controller.list(PORTFOLIO_ID, false, jwt);

        assertThat(response.getData()).isSameAs(data);
        verify(service).list(PORTFOLIO_ID, USER_SUB);
    }

    @Test
    void should_returnOnlyOpenPositions_when_openOnlyFlagIsTrue() {
        when(service.listOpen(PORTFOLIO_ID, USER_SUB)).thenReturn(List.of());

        controller.list(PORTFOLIO_ID, true, jwt);

        verify(service).listOpen(PORTFOLIO_ID, USER_SUB);
    }

    @Test
    void should_delegateToOpenService_when_openInvoked() {
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35.20"), new BigDecimal("1"), null, null);
        when(service.open(eq(PORTFOLIO_ID), eq(USER_SUB), any())).thenReturn(null);

        controller.open(PORTFOLIO_ID, req, jwt);

        verify(service).open(PORTFOLIO_ID, USER_SUB, req);
    }

    @Test
    void should_delegateToCloseService_when_closeInvoked() {
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 5, 1), new BigDecimal("36.00"));
        when(service.close(eq(POSITION_ID), eq(PORTFOLIO_ID), eq(USER_SUB), any())).thenReturn(null);

        controller.close(PORTFOLIO_ID, POSITION_ID, req, jwt);

        verify(service).close(POSITION_ID, PORTFOLIO_ID, USER_SUB, req);
    }

    @Test
    void should_delegateToUpdateCloseService_when_putCloseInvoked() {
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 5, 5), new BigDecimal("37.00"));
        when(service.updateClose(eq(POSITION_ID), eq(PORTFOLIO_ID), eq(USER_SUB), any())).thenReturn(null);

        controller.updateClose(PORTFOLIO_ID, POSITION_ID, req, jwt);

        verify(service).updateClose(POSITION_ID, PORTFOLIO_ID, USER_SUB, req);
    }

    @Test
    void should_delegateToUpdateOpenService_when_putInvoked() {
        UpdateDerivativePositionRequest req = new UpdateDerivativePositionRequest(
                DerivativeDirection.SHORT, LocalDate.of(2026, 4, 15),
                new BigDecimal("36.00"), new BigDecimal("3"));
        when(service.updateOpen(eq(POSITION_ID), eq(PORTFOLIO_ID), eq(USER_SUB), any())).thenReturn(null);

        controller.update(PORTFOLIO_ID, POSITION_ID, req, jwt);

        verify(service).updateOpen(POSITION_ID, PORTFOLIO_ID, USER_SUB, req);
    }

    @Test
    void should_delegateToReopenService_when_patchReopenInvoked() {
        when(service.reopen(POSITION_ID, PORTFOLIO_ID, USER_SUB)).thenReturn(null);

        controller.reopen(PORTFOLIO_ID, POSITION_ID, jwt);

        verify(service).reopen(POSITION_ID, PORTFOLIO_ID, USER_SUB);
    }

    @Test
    void should_delegateToDeleteService_when_deleteInvoked() {
        controller.delete(PORTFOLIO_ID, POSITION_ID, jwt);

        verify(service).delete(POSITION_ID, PORTFOLIO_ID, USER_SUB);
    }
}
