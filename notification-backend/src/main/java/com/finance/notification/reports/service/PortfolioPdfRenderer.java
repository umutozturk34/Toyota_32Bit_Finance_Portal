package com.finance.notification.reports.service;

import com.finance.notification.reports.dto.PortfolioPdfModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;

@Log4j2
@Component
@RequiredArgsConstructor
public class PortfolioPdfRenderer {

    private final TemplateEngine templateEngine;

    public byte[] render(PortfolioPdfModel model) {
        Context ctx = new Context(model.locale());
        ctx.setVariable("portfolio", model.portfolio());
        ctx.setVariable("summary", model.summary());
        ctx.setVariable("positions", model.positions());
        ctx.setVariable("allocation", model.allocation());
        ctx.setVariable("chartImages", model.chartImages());
        ctx.setVariable("currency", model.currency());
        ctx.setVariable("generatedAt", model.generatedAt());

        String html = templateEngine.process(model.theme().templatePath(), ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Portfolio PDF render failed portfolioId={} theme={} locale={} cause={}",
                    model.portfolio() != null ? model.portfolio().id() : null,
                    model.theme(), model.locale(), e.toString(), e);
            throw new PdfRenderException("Failed to render portfolio pdf", e);
        }
    }
}
