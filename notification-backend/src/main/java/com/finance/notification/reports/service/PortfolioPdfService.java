package com.finance.notification.reports.service;

import com.finance.common.exception.BadRequestException;
import com.finance.notification.config.PdfExportProperties;
import com.finance.notification.reports.client.PortfolioDataClient;
import com.finance.notification.reports.dto.PortfolioPdfRequest;
import com.finance.notification.reports.dto.PortfolioReportBundle;
import com.finance.notification.reports.dto.ReportAllocation;
import com.finance.notification.reports.model.ReportPalette;
import com.finance.notification.reports.view.AllocationViewItem;
import com.finance.notification.reports.view.MoneyFormat;
import com.finance.notification.reports.view.RealizedViewItem;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the portfolio PDF report end to end: fetches report data, derives allocation/winners/losers
 * view items, renders donut and performance SVG charts via {@link ReportSvgService}, processes the
 * Thymeleaf HTML template (theme/locale/currency aware) and posts it to the external pdf-service to
 * produce the PDF bytes. Empty portfolios are rejected and pipeline failures surface as
 * {@link PdfGenerationException}.
 */
@Log4j2
@Service
public class PortfolioPdfService {

    private static final String RENDER_PATH = "/render";
    private static final String TEMPLATE = "pdf/portfolio-report";
    private static final String[] ALLOCATION_COLORS = {
            "#6366f1", "#8b5cf6", "#a78bfa", "#22d3ee", "#10b981",
            "#fbbf24", "#fb7185", "#f97316", "#34d399", "#60a5fa"
    };
    private static final DateTimeFormatter GENERATED_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm");
    private static final int MAX_PDF_BYTES = 32 * 1024 * 1024;

    private final WebClient pdfClient;
    private final PortfolioDataClient dataClient;
    private final TemplateEngine templateEngine;
    private final MessageSource messageSource;
    private final ReportSvgService svgService;
    private final Duration requestTimeout;
    private final long requestTimeoutMs;

    public PortfolioPdfService(WebClient.Builder webClientBuilder,
                               PortfolioDataClient dataClient,
                               TemplateEngine templateEngine,
                               MessageSource messageSource,
                               ReportSvgService svgService,
                               PdfExportProperties properties) {
        this.dataClient = dataClient;
        this.templateEngine = templateEngine;
        this.messageSource = messageSource;
        this.svgService = svgService;
        this.pdfClient = webClientBuilder
                .baseUrl(properties.pdf().serviceUrl())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_PDF_BYTES))
                .build();
        this.requestTimeoutMs = properties.pdf().requestTimeoutMs();
        this.requestTimeout = Duration.ofMillis(this.requestTimeoutMs);
    }

    /**
     * Produces the portfolio report PDF for the given request.
     *
     * @param accessToken caller's bearer token, forwarded when fetching their portfolio data
     * @return the rendered PDF bytes
     * @throws BadRequestException when the portfolio has no positions
     * @throws PdfGenerationException when data fetch, rendering or the pdf-service call fails
     */
    public byte[] generate(PortfolioPdfRequest request, String userSub, String accessToken) {
        long start = System.currentTimeMillis();
        try {
            PortfolioReportBundle bundle = dataClient.fetch(request.portfolioId(), "ALL", accessToken);
            if (bundle.positions() == null || bundle.positions().isEmpty()) {
                throw new BadRequestException("error.report.portfolioEmpty");
            }
            Locale locale = "en".equalsIgnoreCase(request.locale()) ? Locale.ENGLISH : new Locale("tr", "TR");
            ReportPalette palette = ReportPalette.of(request.theme());
            String currencySymbol = currencySymbol(request.currency());

            Context ctx = new Context(locale);
            String displayName = (request.portfolioName() == null || request.portfolioName().isBlank())
                    ? messageSource.getMessage("pdf.portfolio.untitled",
                            new Object[]{request.portfolioId()}, locale)
                    : request.portfolioName();
            ctx.setVariable("portfolioName", displayName);
            ctx.setVariable("currency", request.currency());
            ctx.setVariable("currencySymbol", currencySymbol);
            ctx.setVariable("palette", palette);
            ctx.setVariable("locale", locale.getLanguage());
            ctx.setVariable("decimalGroupSep", "tr".equalsIgnoreCase(locale.getLanguage()) ? "POINT" : "COMMA");
            ctx.setVariable("decimalSep", "tr".equalsIgnoreCase(locale.getLanguage()) ? "COMMA" : "POINT");
            ctx.setVariable("money", new MoneyFormat(currencySymbol, locale));
            ctx.setVariable("moneyTry", new MoneyFormat("₺", locale));
            ctx.setVariable("generatedAt", LocalDateTime.now().format(GENERATED_FMT.withLocale(locale)));
            ctx.setVariable("summary", bundle.summary());
            ctx.setVariable("positions", bundle.positions());
            List<AllocationViewItem> allocationItems = buildAllocationItems(bundle.allocation(), locale);
            ctx.setVariable("allocationItems", allocationItems);
            ctx.setVariable("allocationSvg", svgService.allocationDonut(allocationItems, palette));
            ctx.setVariable("winners", buildRealized(bundle.allocation(), true, locale));
            ctx.setVariable("losers", buildRealized(bundle.allocation(), false, locale));
            ctx.setVariable("performanceSvg", svgService.performanceLineChart(bundle.performanceSeries(), palette, locale));

            LocaleContextHolder.setLocale(locale);
            String html = templateEngine.process(TEMPLATE, ctx);

            byte[] pdf = renderPdf(html, request.portfolioId());

            log.info("Portfolio PDF generated portfolioId={} theme={} locale={} bytes={} durationMs={}",
                    request.portfolioId(), request.theme(), request.locale(),
                    pdf.length, System.currentTimeMillis() - start);
            return pdf;
        } catch (PdfGenerationException e) {
            log.error("Portfolio PDF generation failed portfolioId={} durationMs={} cause={}",
                    request.portfolioId(), System.currentTimeMillis() - start, e.toString());
            throw e;
        } catch (BadRequestException e) {
            log.info("Portfolio PDF rejected portfolioId={} reason={}", request.portfolioId(), e.getMessage());
            throw e;
        } catch (WebClientResponseException e) {
            log.error("Portfolio PDF http failure portfolioId={} status={} body={} durationMs={}",
                    request.portfolioId(), e.getStatusCode(),
                    e.getResponseBodyAsString(StandardCharsets.UTF_8),
                    System.currentTimeMillis() - start, e);
            throw new PdfGenerationException("pdf pipeline http error: " + e.getStatusCode(), e);
        } catch (RuntimeException e) {
            log.error("Portfolio PDF generation failed portfolioId={} durationMs={} cause={}",
                    request.portfolioId(), System.currentTimeMillis() - start, e.toString(), e);
            throw new PdfGenerationException("Failed to generate portfolio pdf", e);
        }
    }

    private byte[] renderPdf(String html, Long portfolioId) {
        Map<String, Object> pdfOptions = new LinkedHashMap<>();
        pdfOptions.put("format", "A4");
        pdfOptions.put("printBackground", true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("htmlContent", html);
        body.put("timeout", requestTimeoutMs);
        body.put("pdfOptions", pdfOptions);

        byte[] pdf = pdfClient.post()
                .uri(RENDER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_PDF)
                .bodyValue(body)
                .retrieve()
                .onStatus(s -> s.isError(),
                        r -> r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new PdfGenerationException(
                                        "pdf-service returned " + r.statusCode() + ": " + msg))))
                .bodyToMono(byte[].class)
                .block(requestTimeout);

        if (pdf == null || pdf.length == 0) {
            throw new PdfGenerationException("pdf-service returned empty body for portfolio " + portfolioId);
        }
        return pdf;
    }

    private List<AllocationViewItem> buildAllocationItems(List<ReportAllocation> raw, Locale locale) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<ReportAllocation> filtered = raw.stream()
                .filter(a -> a.valueTry() != null && a.valueTry().abs().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing((ReportAllocation a) -> a.valueTry().abs()).reversed())
                .toList();
        BigDecimal total = filtered.stream()
                .map(a -> a.valueTry().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) return List.of();

        List<AllocationViewItem> out = new java.util.ArrayList<>(filtered.size());
        for (int i = 0; i < filtered.size(); i++) {
            ReportAllocation a = filtered.get(i);
            BigDecimal value = a.valueTry().abs();
            BigDecimal sharePct = value.multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP);
            out.add(new AllocationViewItem(
                    translateAssetLabel(a, locale),
                    value,
                    sharePct,
                    ALLOCATION_COLORS[i % ALLOCATION_COLORS.length]
            ));
        }
        return out;
    }

    /** Builds the winners or losers list from realized P/L, sorted by magnitude with bar widths relative to the largest. */
    private List<RealizedViewItem> buildRealized(List<ReportAllocation> raw, boolean winners, Locale locale) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<ReportAllocation> filtered = raw.stream()
                .filter(a -> a.realizedPnlTry() != null && a.realizedPnlTry().signum() != 0)
                .filter(a -> winners ? a.realizedPnlTry().signum() > 0 : a.realizedPnlTry().signum() < 0)
                .sorted(winners
                        ? Comparator.comparing(ReportAllocation::realizedPnlTry).reversed()
                        : Comparator.comparing(ReportAllocation::realizedPnlTry))
                .toList();
        if (filtered.isEmpty()) return List.of();

        BigDecimal maxAbs = filtered.stream()
                .map(a -> a.realizedPnlTry().abs())
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ONE);

        return filtered.stream()
                .map(a -> new RealizedViewItem(
                        translateAssetLabel(a, locale),
                        a.realizedPnlTry().abs(),
                        a.costTry() != null ? a.costTry() : BigDecimal.ZERO,
                        maxAbs.signum() == 0 ? 0d
                                : a.realizedPnlTry().abs()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(maxAbs, 2, RoundingMode.HALF_UP)
                                .doubleValue()
                ))
                .toList();
    }

    private String translateAssetLabel(ReportAllocation a, Locale locale) {
        String type = a.assetType() != null ? a.assetType() : a.label();
        if (type == null) return "—";
        return messageSource.getMessage("market.type." + type, null, type, locale);
    }

    private String currencySymbol(String currency) {
        if (currency == null) return "₺";
        return switch (currency.toUpperCase(Locale.ROOT)) {
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> "₺";
        };
    }
}
