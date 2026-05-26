package com.finance.notification.reports.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.notification.portfolio.PortfolioOwnershipReader;
import com.finance.notification.reports.dto.PortfolioPdfRequest;
import com.finance.notification.reports.service.PortfolioPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportsControllerTest {

    @Mock private PortfolioPdfService service;
    @Mock private PortfolioOwnershipReader ownershipReader;
    @InjectMocks private ReportsController controller;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setProviderClass(org.hibernate.validator.HibernateValidator.class);
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setHandlerExceptionResolvers(new DefaultHandlerExceptionResolver())
                .build();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-sub")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.put("sub", "user-sub"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt, null));
    }

    @Test
    void post_returnsPdfStreamWithAttachmentHeaderWhenOwner() throws Exception {
        when(ownershipReader.isOwner(1L, "user-sub")).thenReturn(true);
        when(service.generate(any(PortfolioPdfRequest.class), eq("user-sub"), eq("token")))
                .thenReturn(new byte[]{37, 80, 68, 70});
        String body = mapper.writeValueAsString(valid());

        mockMvc.perform(post("/api/v1/reports/portfolio-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"portfolio-1-")));
    }

    @Test
    void post_throwsAccessDeniedWhenCallerDoesNotOwnPortfolio() throws Exception {
        when(ownershipReader.isOwner(anyLong(), anyString())).thenReturn(false);
        String body = mapper.writeValueAsString(valid());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        mockMvc.perform(post("/api/v1/reports/portfolio-pdf")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
        verify(service, never()).generate(any(), anyString(), anyString());
    }

    @Test
    void post_returns400WhenLocaleInvalid() throws Exception {
        PortfolioPdfRequest bad = new PortfolioPdfRequest(1L, null, "LIGHT", "fr", "TRY");
        String body = mapper.writeValueAsString(bad);

        mockMvc.perform(post("/api/v1/reports/portfolio-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_returns400WhenCurrencyInvalid() throws Exception {
        PortfolioPdfRequest bad = new PortfolioPdfRequest(1L, null, "LIGHT", "tr", "GBP");
        String body = mapper.writeValueAsString(bad);

        mockMvc.perform(post("/api/v1/reports/portfolio-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_returns400WhenThemeInvalid() throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "portfolioId", 1, "theme", "PURPLE", "locale", "tr", "currency", "TRY"));

        mockMvc.perform(post("/api/v1/reports/portfolio-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private PortfolioPdfRequest valid() {
        return new PortfolioPdfRequest(1L, null, "LIGHT", "tr", "TRY");
    }

}
