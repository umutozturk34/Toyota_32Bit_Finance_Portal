package com.finance.notification.reports.service;

import com.finance.common.exception.BadRequestException;
import com.finance.notification.config.PdfExportProperties;
import com.finance.notification.reports.client.ForexHistoryClient;
import com.finance.notification.reports.client.PortfolioDataClient;
import com.finance.notification.reports.dto.PerformanceSeriesPoint;
import com.finance.notification.reports.dto.PortfolioPdfRequest;
import com.finance.notification.reports.dto.PortfolioReportBundle;
import com.finance.notification.reports.dto.ReportAllocation;
import com.finance.notification.reports.dto.ReportPosition;
import com.finance.notification.reports.dto.ReportSummary;
import com.finance.notification.reports.fx.ForexRatePoint;
import com.finance.notification.reports.fx.ReportFxConverter;
import com.finance.notification.reports.model.ReportPalette;
import com.finance.notification.reports.view.AllocationViewItem;
import com.finance.notification.reports.view.MoneyFormat;
import com.finance.notification.reports.view.PercentFormat;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    private static final String TRY = "TRY";
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final WebClient pdfClient;
    private final PortfolioDataClient dataClient;
    private final ForexHistoryClient forexHistoryClient;
    private final TemplateEngine templateEngine;
    private final MessageSource messageSource;
    private final ReportSvgService svgService;
    private final ReportCurrencyConverter reportFxConverter;
    private final Duration requestTimeout;
    private final long requestTimeoutMs;

    public PortfolioPdfService(WebClient.Builder webClientBuilder,
                               PortfolioDataClient dataClient,
                               ForexHistoryClient forexHistoryClient,
                               TemplateEngine templateEngine,
                               MessageSource messageSource,
                               ReportSvgService svgService,
                               ReportCurrencyConverter reportFxConverter,
                               PdfExportProperties properties) {
        this.dataClient = dataClient;
        this.forexHistoryClient = forexHistoryClient;
        this.templateEngine = templateEngine;
        this.messageSource = messageSource;
        this.svgService = svgService;
        this.reportFxConverter = reportFxConverter;
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
            String requested = request.currency() == null ? TRY : request.currency().toUpperCase(Locale.ROOT);
            // Start the USD/EUR rate-history round-trip concurrently with the portfolio data fetch so it
            // overlaps the 4-way data fan-out instead of stacking sequentially after it — a full,
            // VDS-amplified round-trip saved on every non-TRY report (TRY reports skip FX entirely). The
            // range stays "ALL": a lot's entry date can predate the report window and must convert at its
            // own historical rate, so the window cannot be trimmed without corrupting older lots.
            CompletableFuture<List<ForexRatePoint>> fxFuture = TRY.equals(requested)
                    ? CompletableFuture.completedFuture(List.of())
                    : CompletableFuture.supplyAsync(() -> forexHistoryClient.fetchHistory(requested, accessToken));

            long fetchStart = System.currentTimeMillis();
            PortfolioReportBundle bundle = dataClient.fetch(request.portfolioId(), "5Y", accessToken);
            long fetchMs = System.currentTimeMillis() - fetchStart;
            if (bundle.positions() == null || bundle.positions().isEmpty()) {
                fxFuture.cancel(true);
                throw new BadRequestException("error.report.portfolioEmpty");
            }
            Locale locale = "en".equalsIgnoreCase(request.locale()) ? Locale.ENGLISH : new Locale("tr", "TR");
            ReportPalette palette = ReportPalette.of(request.theme());
            // fxMs is now just the residual wait after the data fetch — ~0 when the FX round-trip already
            // finished concurrently, so it no longer stacks on the critical path for non-TRY reports.
            long fxStart = System.currentTimeMillis();
            List<ForexRatePoint> rates = reportFxConverter.joinFxRates(fxFuture, requested);
            long fxMs = System.currentTimeMillis() - fxStart;
            // A non-TRY report with no FX history available falls back to TRY rather than printing raw
            // lira magnitudes under a $/€ symbol.
            String target = (!TRY.equals(requested) && rates.isEmpty()) ? TRY : requested;
            String currencySymbol = currencySymbol(target);
            ReportFxConverter fx = TRY.equals(target)
                    ? new ReportFxConverter(TRY, Map.of())
                    : new ReportFxConverter(target, Map.of(target, rates));

            // Current figures (summary totals, current price/market value/P&L, allocation) are valued
            // "now", so convert them at today's rate to match the on-screen cards, which use the live
            // rate. Entry values keep their entry-date rate; the performance series converts each point
            // at its own date.
            LocalDate asOf = LocalDate.now(ISTANBUL);

            ReportSummary summary = reportFxConverter.convertSummary(bundle.summary(), fx, asOf, target);
            List<ReportPosition> positions = reportFxConverter.convertPositions(bundle.positions(), fx, asOf);
            List<ReportAllocation> allocation = reportFxConverter.convertAllocations(bundle.allocation(), fx, asOf, target);
            List<ReportAllocation> realizedAllocation = reportFxConverter.convertAllocations(bundle.realizedAllocation(), fx, asOf, target);
            List<PerformanceSeriesPoint> performanceSeries = reportFxConverter.convertSeries(bundle.performanceSeries(), fx);

            Context ctx = new Context(locale);
            String displayName = (request.portfolioName() == null || request.portfolioName().isBlank())
                    ? messageSource.getMessage("pdf.portfolio.untitled",
                            new Object[]{request.portfolioId()}, locale)
                    : request.portfolioName();
            ctx.setVariable("portfolioName", displayName);
            ctx.setVariable("currency", target);
            ctx.setVariable("currencySymbol", currencySymbol);
            ctx.setVariable("palette", palette);
            ctx.setVariable("locale", locale.getLanguage());
            ctx.setVariable("decimalGroupSep", "tr".equalsIgnoreCase(locale.getLanguage()) ? "POINT" : "COMMA");
            ctx.setVariable("decimalSep", "tr".equalsIgnoreCase(locale.getLanguage()) ? "COMMA" : "POINT");
            ctx.setVariable("money", new MoneyFormat(currencySymbol, locale));
            ctx.setVariable("moneyTry", new MoneyFormat("₺", locale));
            ctx.setVariable("pct", new PercentFormat(locale));
            ctx.setVariable("generatedAt", LocalDateTime.now().format(GENERATED_FMT.withLocale(locale)));
            ctx.setVariable("summary", summary);
            ctx.setVariable("positions", positions);
            List<AllocationViewItem> allocationItems = buildAllocationItems(allocation, locale);
            ctx.setVariable("allocationItems", allocationItems);
            ctx.setVariable("allocationSvg", svgService.allocationDonut(allocationItems, palette));
            // Winners/Losers feed off the realizedPnl allocation (per-asset-type rows like
            // "Hisse", "Kripto", "VİOP"), NOT the regular allocation pie which collapses every
            // closed lot into one synthetic CASH bucket — that previously rendered the section
            // as a single "Nakit" entry with no per-asset detail.
            ctx.setVariable("winners", buildRealized(realizedAllocation, true, locale));
            ctx.setVariable("losers", buildRealized(realizedAllocation, false, locale));
            // The value chart carries the target currency symbol on its y-axis; the return chart is a
            // percentage axis, so it is passed unchanged with an empty symbol.
            ctx.setVariable("performanceSvg", svgService.performanceLineChart(performanceSeries, palette, locale, currencySymbol));
            ctx.setVariable("returnPercentSvg", svgService.performanceLineChart(bundle.returnSeries(), palette, locale, ""));

            LocaleContextHolder.setLocale(locale);
            String html = templateEngine.process(TEMPLATE, ctx);

            long renderStart = System.currentTimeMillis();
            byte[] pdf = renderPdf(html, request.portfolioId());
            long renderMs = System.currentTimeMillis() - renderStart;

            log.info("Portfolio PDF generated portfolioId={} theme={} locale={} bytes={} "
                            + "durationMs={} fetchMs={} fxMs={} renderMs={}",
                    request.portfolioId(), request.theme(), request.locale(),
                    pdf.length, System.currentTimeMillis() - start, fetchMs, fxMs, renderMs);
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

    /**
     * Joins the FX history fetch that was started concurrently with the data fetch. Unwraps the
     * {@link CompletionException} so a downstream failure surfaces as its original runtime exception —
     * caught by {@link #generate}'s existing handler exactly as the previous sequential call was.
     */
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

        List<BigDecimal> values = filtered.stream().map(a -> a.valueTry().abs()).toList();
        // Largest-remainder (Hamilton) at 1 decimal so the displayed shares sum to EXACTLY 100,0 instead of
        // drifting (e.g. 46,9+29,0+14,3+9,2+0,7 = 100,1) from independently-rounded slices. Mirrors the web
        // AllocationChart (shared/utils/percent.js) so the PDF legend and the app agree.
        List<BigDecimal> shares = largestRemainderPercents(values, total, 1);
        List<AllocationViewItem> out = new java.util.ArrayList<>(filtered.size());
        for (int i = 0; i < filtered.size(); i++) {
            out.add(new AllocationViewItem(
                    translateAssetLabel(filtered.get(i), locale),
                    values.get(i),
                    shares.get(i),
                    ALLOCATION_COLORS[i % ALLOCATION_COLORS.length]
            ));
        }
        return out;
    }

    /**
     * Largest-remainder (Hamilton) percentages at {@code scale} decimals whose sum is EXACTLY 100. Rounding
     * each share independently makes a legend read e.g. 100,1; this floors every share to the target precision
     * then hands the leftover last-digit units to the largest fractional remainders so the slices add to 100,0.
     */
    static List<BigDecimal> largestRemainderPercents(List<BigDecimal> values, BigDecimal total, int scale) {
        int n = values.size();
        if (n == 0 || total == null || total.signum() == 0) {
            return values.stream().map(v -> BigDecimal.ZERO.setScale(scale)).toList();
        }
        long targetUnits = 100L * BigDecimal.TEN.pow(scale).longValueExact();
        long[] units = new long[n];
        double[] remainder = new double[n];
        long assigned = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal exact = values.get(i).multiply(BigDecimal.valueOf(targetUnits))
                    .divide(total, 10, RoundingMode.HALF_UP);
            long floor = exact.setScale(0, RoundingMode.FLOOR).longValueExact();
            units[i] = floor;
            remainder[i] = exact.subtract(BigDecimal.valueOf(floor)).doubleValue();
            assigned += floor;
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (x, y) -> Double.compare(remainder[y], remainder[x]));
        for (int k = 0, left = (int) (targetUnits - assigned); k < n && left > 0; k++, left--) {
            units[order[k]]++;
        }
        List<BigDecimal> out = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(BigDecimal.valueOf(units[i], scale));
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
