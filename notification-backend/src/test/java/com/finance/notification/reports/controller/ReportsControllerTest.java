package com.finance.notification.reports.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.notification.reports.dto.PortfolioPdfRequest;
import com.finance.notification.reports.dto.ThemeVariant;
import com.finance.notification.reports.service.PortfolioPdfService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportsControllerTest {

    @Mock private PortfolioPdfService service;
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
                .build();
    }

    @Test
    void post_returnsPdfStreamWithAttachmentHeader() throws Exception {
        // Arrange
        when(service.generate(any(PortfolioPdfRequest.class))).thenReturn(new byte[]{37, 80, 68, 70});
        String body = mapper.writeValueAsString(valid());

        // Act + Assert
        mockMvc.perform(post("/api/v1/reports/portfolio-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"portfolio-")));
    }

    @Test
    void post_returns400WhenLocaleInvalid() throws Exception {
        // Arrange
        var bad = new PortfolioPdfRequest(
                new PortfolioPdfRequest.Portfolio(1L, "x", "u@x.com"),
                new PortfolioPdfRequest.Summary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                List.of(), List.of(), Map.of(),
                "TRY", ThemeVariant.LIGHT, "fr");
        String body = mapper.writeValueAsString(bad);

        // Act + Assert
        mockMvc.perform(post("/api/v1/reports/portfolio-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_returns400WhenCurrencyInvalid() throws Exception {
        // Arrange
        var bad = new PortfolioPdfRequest(
                new PortfolioPdfRequest.Portfolio(1L, "x", "u@x.com"),
                new PortfolioPdfRequest.Summary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                List.of(), List.of(), Map.of(),
                "GBP", ThemeVariant.LIGHT, "tr");
        String body = mapper.writeValueAsString(bad);

        // Act + Assert
        mockMvc.perform(post("/api/v1/reports/portfolio-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private PortfolioPdfRequest valid() {
        return new PortfolioPdfRequest(
                new PortfolioPdfRequest.Portfolio(1L, "Ana", "u@x.com"),
                new PortfolioPdfRequest.Summary(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                List.of(), List.of(), Map.of(),
                "TRY", ThemeVariant.LIGHT, "tr");
    }
}
