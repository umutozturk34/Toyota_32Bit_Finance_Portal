package com.finance.notification.reports.service;

import com.finance.notification.reports.dto.PortfolioPdfModel;
import com.finance.notification.reports.dto.PortfolioPdfRequest;
import com.finance.notification.reports.dto.ThemeVariant;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioPdfRendererTest {

    private PortfolioPdfRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        engine.setTemplateEngineMessageSource(ms);
        renderer = new PortfolioPdfRenderer(engine);
    }

    @ParameterizedTest
    @CsvSource({
            "LIGHT, tr",
            "LIGHT, en",
            "DARK,  tr",
            "DARK,  en"
    })
    void render_producesPdfContainingPortfolioAndPosition(ThemeVariant theme, String localeTag) throws Exception {
        // Arrange
        PortfolioPdfModel model = sampleModel(theme, Locale.forLanguageTag(localeTag));

        // Act
        byte[] pdf = renderer.render(model);

        // Assert
        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Ana");
            assertThat(text).contains("KCHOL.IS");
        }
    }

    private PortfolioPdfModel sampleModel(ThemeVariant theme, Locale locale) {
        return new PortfolioPdfModel(
                new PortfolioPdfRequest.Portfolio(1L, "Ana", "u@x.com"),
                new PortfolioPdfRequest.Summary(
                        new BigDecimal("12345.67"), new BigDecimal("10000.00"),
                        new BigDecimal("2345.67"), new BigDecimal("23.46"),
                        new BigDecimal("123.45"), new BigDecimal("1.01")),
                List.of(new PortfolioPdfRequest.Position(
                        "KCHOL.IS", "Koc Holding", "STOCK",
                        new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("175"),
                        new BigDecimal("1750"), new BigDecimal("750"), new BigDecimal("75"))),
                List.of(new PortfolioPdfRequest.AllocationSlice("Hisse", new BigDecimal("100"), "#10b981")),
                Map.of(),
                "TRY", theme, locale, LocalDateTime.of(2026, 5, 24, 12, 0));
    }
}
