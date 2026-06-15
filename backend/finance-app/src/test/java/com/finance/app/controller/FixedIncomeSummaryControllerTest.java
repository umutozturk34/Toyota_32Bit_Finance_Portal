package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.i18n.Translator;
import com.finance.portfolio.dto.response.FixedIncomeHistoryPoint;
import com.finance.portfolio.dto.response.FixedIncomeSummaryResponse;
import com.finance.portfolio.fixedincome.FixedIncomeSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FixedIncomeSummaryController}, mirroring the sibling {@code BondHoldingControllerTest}
 * plain-Mockito style: a constructed controller, mocked service + translator, and a built {@link Jwt}. Asserts
 * the envelope + key, delegation with the JWT subject (and default period passthrough), and that a service
 * {@link ResourceNotFoundException} (the ownership 404) propagates rather than being swallowed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixedIncomeSummaryControllerTest {

    private static final Long PORTFOLIO_ID = 1L;
    private static final String USER_SUB = "user-1";

    @Mock private FixedIncomeSummaryService service;
    @Mock private Translator translator;

    private FixedIncomeSummaryController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new FixedIncomeSummaryController(service, translator);
        when(translator.translate(any())).thenAnswer(inv -> inv.getArgument(0));
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER_SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", USER_SUB)
                .build();
    }

    @Test
    void should_returnSummary_when_summaryInvoked() {
        FixedIncomeSummaryResponse data = mock(FixedIncomeSummaryResponse.class);
        when(service.summary(PORTFOLIO_ID, USER_SUB)).thenReturn(data);

        ApiResponse<FixedIncomeSummaryResponse> response = controller.summary(PORTFOLIO_ID, jwt);

        assertThat(response.getData()).isSameAs(data);
        assertThat(response.getMessage()).isEqualTo("api.portfolio.fixedIncome.summaryRetrieved");
        verify(service).summary(PORTFOLIO_ID, USER_SUB);
    }

    @Test
    void should_returnHistory_when_historyInvokedWithPeriod() {
        List<FixedIncomeHistoryPoint> data = List.of();
        when(service.history(PORTFOLIO_ID, USER_SUB, "3Y")).thenReturn(data);

        ApiResponse<List<FixedIncomeHistoryPoint>> response = controller.history(PORTFOLIO_ID, "3Y", jwt);

        assertThat(response.getData()).isSameAs(data);
        assertThat(response.getMessage()).isEqualTo("api.portfolio.fixedIncome.historyRetrieved");
        verify(service).history(PORTFOLIO_ID, USER_SUB, "3Y");
    }

    @Test
    void should_propagateNotFound_when_summaryServiceThrowsResourceNotFound() {
        when(service.summary(PORTFOLIO_ID, USER_SUB))
                .thenThrow(new ResourceNotFoundException("error.portfolio.notFound", PORTFOLIO_ID));

        assertThatThrownBy(() -> controller.summary(PORTFOLIO_ID, jwt))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_propagateNotFound_when_historyServiceThrowsResourceNotFound() {
        when(service.history(PORTFOLIO_ID, USER_SUB, "1Y"))
                .thenThrow(new ResourceNotFoundException("error.portfolio.notFound", PORTFOLIO_ID));

        assertThatThrownBy(() -> controller.history(PORTFOLIO_ID, "1Y", jwt))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_failUnauthenticated_when_noJwtPrincipalPresent() {
        assertThatThrownBy(() -> controller.summary(PORTFOLIO_ID, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }
}
