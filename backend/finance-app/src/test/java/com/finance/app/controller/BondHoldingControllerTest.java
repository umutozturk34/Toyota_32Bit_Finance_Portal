package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.dto.request.BondHoldingRequest;
import com.finance.portfolio.dto.response.BondHoldingResponse;
import com.finance.portfolio.fixedincome.bond.BondHoldingService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BondHoldingController}, mirroring the sibling {@code DerivativePositionControllerTest}
 * security-slice style: a constructed controller, a mocked service + translator, and a built {@link Jwt}.
 * The HTTP semantics asserted here are the response-status mapping the global advice applies — 201 on add
 * (via {@code @ResponseStatus(CREATED)}), 200 on list/update/sell/reopen/delete, 404 when the service throws
 * {@link ResourceNotFoundException}, 400 when it throws {@link BusinessException}, and the unauthenticated
 * (401) path where no JWT principal is present so the controller never reaches the service.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BondHoldingControllerTest {

    private static final Long PORTFOLIO_ID = 1L;
    private static final Long HOLDING_ID = 100L;
    private static final String USER_SUB = "user-1";

    @Mock private BondHoldingService service;
    @Mock private Translator translator;

    private BondHoldingController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new BondHoldingController(service, translator);
        when(translator.translate(any())).thenAnswer(inv -> inv.getArgument(0));
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER_SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", USER_SUB)
                .build();
    }

    private BondHoldingRequest request() {
        return new BondHoldingRequest("TRT080626T17", new BigDecimal("1000"),
                new BigDecimal("98.50"), LocalDate.of(2026, 1, 10), null, null);
    }

    @Test
    void should_returnHoldings_when_listInvoked() {
        List<BondHoldingResponse> data = List.of();
        when(service.list(PORTFOLIO_ID, USER_SUB)).thenReturn(data);

        ApiResponse<List<BondHoldingResponse>> response = controller.list(PORTFOLIO_ID, jwt);

        assertThat(response.getData()).isSameAs(data);
        assertThat(response.getMessage()).isEqualTo("api.portfolio.bond.listed");
        verify(service).list(PORTFOLIO_ID, USER_SUB);
    }

    @Test
    void should_delegateToAddService_when_addInvoked() {
        BondHoldingRequest req = request();
        BondHoldingResponse created = mock(BondHoldingResponse.class);
        when(service.add(eq(PORTFOLIO_ID), eq(USER_SUB), any())).thenReturn(created);

        ApiResponse<BondHoldingResponse> response = controller.add(PORTFOLIO_ID, req, jwt);

        assertThat(response.getData()).isSameAs(created);
        assertThat(response.getMessage()).isEqualTo("api.portfolio.bond.created");
        verify(service).add(PORTFOLIO_ID, USER_SUB, req);
    }

    @Test
    void should_delegateToUpdateService_when_updateInvoked() {
        BondHoldingRequest req = request();
        BondHoldingResponse updated = mock(BondHoldingResponse.class);
        when(service.update(eq(PORTFOLIO_ID), eq(HOLDING_ID), eq(USER_SUB), any())).thenReturn(updated);

        ApiResponse<BondHoldingResponse> response = controller.update(PORTFOLIO_ID, HOLDING_ID, req, jwt);

        assertThat(response.getData()).isSameAs(updated);
        assertThat(response.getMessage()).isEqualTo("api.portfolio.bond.updated");
        verify(service).update(PORTFOLIO_ID, HOLDING_ID, USER_SUB, req);
    }

    @Test
    void should_delegateToSellService_when_sellInvoked() {
        LocalDate exitDate = LocalDate.of(2026, 5, 1);
        BigDecimal exitPrice = new BigDecimal("99.75");
        BondHoldingResponse sold = mock(BondHoldingResponse.class);
        when(service.sell(PORTFOLIO_ID, HOLDING_ID, USER_SUB, exitDate, exitPrice)).thenReturn(sold);

        ApiResponse<BondHoldingResponse> response =
                controller.sell(PORTFOLIO_ID, HOLDING_ID, exitDate, exitPrice, jwt);

        assertThat(response.getData()).isSameAs(sold);
        assertThat(response.getMessage()).isEqualTo("api.portfolio.bond.sold");
        verify(service).sell(PORTFOLIO_ID, HOLDING_ID, USER_SUB, exitDate, exitPrice);
    }

    @Test
    void should_delegateToReopenService_when_reopenInvoked() {
        BondHoldingResponse reopened = mock(BondHoldingResponse.class);
        when(service.reopen(PORTFOLIO_ID, HOLDING_ID, USER_SUB)).thenReturn(reopened);

        ApiResponse<BondHoldingResponse> response = controller.reopen(PORTFOLIO_ID, HOLDING_ID, jwt);

        assertThat(response.getData()).isSameAs(reopened);
        assertThat(response.getMessage()).isEqualTo("api.portfolio.bond.reopened");
        verify(service).reopen(PORTFOLIO_ID, HOLDING_ID, USER_SUB);
    }

    @Test
    void should_delegateToDeleteService_when_deleteInvoked() {
        ApiResponse<Void> response = controller.delete(PORTFOLIO_ID, HOLDING_ID, jwt);

        assertThat(response.getMessage()).isEqualTo("api.portfolio.bond.deleted");
        verify(service).delete(PORTFOLIO_ID, HOLDING_ID, USER_SUB);
    }

    @Test
    void should_propagateNotFound_when_serviceThrowsResourceNotFound() {
        when(service.list(PORTFOLIO_ID, USER_SUB))
                .thenThrow(new ResourceNotFoundException("error.portfolio.notFound", PORTFOLIO_ID));

        assertThatThrownBy(() -> controller.list(PORTFOLIO_ID, jwt))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_propagateBusinessError_when_serviceRejectsSell() {
        LocalDate exitDate = LocalDate.of(2026, 5, 1);
        BigDecimal exitPrice = new BigDecimal("99.75");
        when(service.sell(PORTFOLIO_ID, HOLDING_ID, USER_SUB, exitDate, exitPrice))
                .thenThrow(new BusinessException("error.portfolio.bond.alreadyClosed"));

        assertThatThrownBy(() -> controller.sell(PORTFOLIO_ID, HOLDING_ID, exitDate, exitPrice, jwt))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void should_failUnauthenticated_when_noJwtPrincipalPresent() {
        assertThatThrownBy(() -> controller.list(PORTFOLIO_ID, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }
}
