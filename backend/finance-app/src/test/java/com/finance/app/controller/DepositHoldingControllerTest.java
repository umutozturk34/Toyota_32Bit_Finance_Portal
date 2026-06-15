package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.dto.request.DepositHoldingRequest;
import com.finance.portfolio.dto.response.DepositHoldingResponse;
import com.finance.portfolio.fixedincome.deposit.DepositHoldingService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioural coverage for the deposit holdings endpoints, mirroring the derivative controller test style
 * (direct controller invocation with a mocked service + translator). Each method asserts the success envelope
 * + delegation; the create method's HTTP 201 comes from its {@code @ResponseStatus(CREATED)} annotation, the
 * 404 path is the {@link ResourceNotFoundException} propagated to the global advice, the 400 path is the
 * JSR-380 contract on {@link DepositHoldingRequest}, and 401 is enforced upstream by the security filter chain.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepositHoldingControllerTest {

    private static final Long PORTFOLIO_ID = 1L;
    private static final Long HOLDING_ID = 100L;
    private static final String USER_SUB = "user-1";

    private static ValidatorFactory factory;
    private static Validator validator;

    @Mock private DepositHoldingService service;
    @Mock private Translator translator;

    private DepositHoldingController controller;
    private Jwt jwt;

    @BeforeAll
    static void beanValidation() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidation() {
        factory.close();
    }

    @BeforeEach
    void setUp() {
        controller = new DepositHoldingController(service, translator);
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER_SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", USER_SUB)
                .build();
    }

    private DepositHoldingRequest validRequest() {
        return new DepositHoldingRequest("TRY", new BigDecimal("100000"), new BigDecimal("45.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 1));
    }

    @Test
    void should_returnListedEnvelope_when_listInvoked() {
        List<DepositHoldingResponse> data = List.of();
        when(service.list(PORTFOLIO_ID, USER_SUB)).thenReturn(data);

        ApiResponse<List<DepositHoldingResponse>> response = controller.list(PORTFOLIO_ID, jwt);

        assertThat(response.getMessage()).isEqualTo("api.portfolio.deposit.listed");
        assertThat(response.getData()).isSameAs(data);
        verify(service).list(PORTFOLIO_ID, USER_SUB);
    }

    @Test
    void should_delegateToAddService_when_addInvoked() {
        DepositHoldingRequest request = validRequest();
        when(service.add(eq(PORTFOLIO_ID), eq(USER_SUB), any())).thenReturn(null);

        ApiResponse<DepositHoldingResponse> response = controller.add(PORTFOLIO_ID, request, jwt);

        assertThat(response.getMessage()).isEqualTo("api.portfolio.deposit.created");
        verify(service).add(PORTFOLIO_ID, USER_SUB, request);
    }

    @Test
    void should_delegateToUpdateService_when_updateInvoked() {
        DepositHoldingRequest request = validRequest();
        when(service.update(eq(PORTFOLIO_ID), eq(HOLDING_ID), eq(USER_SUB), any())).thenReturn(null);

        ApiResponse<DepositHoldingResponse> response = controller.update(PORTFOLIO_ID, HOLDING_ID, request, jwt);

        assertThat(response.getMessage()).isEqualTo("api.portfolio.deposit.updated");
        verify(service).update(PORTFOLIO_ID, HOLDING_ID, USER_SUB, request);
    }

    @Test
    void should_passCloseDate_when_closeInvokedWithParam() {
        LocalDate when = LocalDate.of(2026, 5, 1);
        when(service.close(eq(PORTFOLIO_ID), eq(HOLDING_ID), eq(USER_SUB), eq(when))).thenReturn(null);

        ApiResponse<DepositHoldingResponse> response = controller.close(PORTFOLIO_ID, HOLDING_ID, when, jwt);

        assertThat(response.getMessage()).isEqualTo("api.portfolio.deposit.closed");
        verify(service).close(PORTFOLIO_ID, HOLDING_ID, USER_SUB, when);
    }

    @Test
    void should_passNullCloseDate_when_closeInvokedWithoutParam() {
        when(service.close(eq(PORTFOLIO_ID), eq(HOLDING_ID), eq(USER_SUB), isNull())).thenReturn(null);

        controller.close(PORTFOLIO_ID, HOLDING_ID, null, jwt);

        verify(service).close(PORTFOLIO_ID, HOLDING_ID, USER_SUB, null);
    }

    @Test
    void should_delegateToReopenService_when_reopenInvoked() {
        when(service.reopen(PORTFOLIO_ID, HOLDING_ID, USER_SUB)).thenReturn(null);

        ApiResponse<DepositHoldingResponse> response = controller.reopen(PORTFOLIO_ID, HOLDING_ID, jwt);

        assertThat(response.getMessage()).isEqualTo("api.portfolio.deposit.reopened");
        verify(service).reopen(PORTFOLIO_ID, HOLDING_ID, USER_SUB);
    }

    @Test
    void should_returnDeletedEnvelope_when_deleteInvoked() {
        ApiResponse<Void> response = controller.delete(PORTFOLIO_ID, HOLDING_ID, jwt);

        assertThat(response.getMessage()).isEqualTo("api.portfolio.deposit.deleted");
        verify(service).delete(PORTFOLIO_ID, HOLDING_ID, USER_SUB);
    }

    @Test
    void should_propagateNotFound_when_serviceThrowsForUnownedPortfolio() {
        when(service.list(PORTFOLIO_ID, USER_SUB))
                .thenThrow(new ResourceNotFoundException("error.portfolio.notFound", PORTFOLIO_ID));

        assertThatThrownBy(() -> controller.list(PORTFOLIO_ID, jwt))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_reportViolations_when_requestBodyInvalid() {
        DepositHoldingRequest invalid = new DepositHoldingRequest(
                "tl", new BigDecimal("-1"), null, null, null, null);

        assertThat(validator.validate(invalid)).isNotEmpty();
    }

    @Test
    void should_haveNoViolations_when_requestBodyValid() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }
}
