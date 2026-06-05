package com.finance.notification.reports.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.notification.config.PdfExportProperties;
import com.finance.notification.reports.client.ForexHistoryClient;
import com.finance.notification.reports.client.PortfolioDataClient;
import com.finance.notification.reports.dto.PortfolioPdfRequest;
import com.finance.notification.reports.dto.PortfolioReportBundle;
import com.finance.notification.reports.dto.ReportAllocation;
import com.finance.notification.reports.dto.ReportPosition;
import com.finance.notification.reports.fx.ForexRatePoint;
import com.finance.notification.reports.view.RealizedViewItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioPdfServiceTest {

    @Mock private PortfolioDataClient dataClient;
    @Mock private ForexHistoryClient forexHistoryClient;
    @Mock private TemplateEngine templateEngine;
    @Mock private MessageSource messageSource;
    @Mock private ReportSvgService svgService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();
    private final List<ClientResponse> queuedResponses = new ArrayList<>();

    private PortfolioPdfService buildService() {
        ExchangeFunction exchange = req -> {
            lastRequest.set(req);
            if (queuedResponses.isEmpty()) {
                return Mono.error(new IllegalStateException("no response queued"));
            }
            return Mono.just(queuedResponses.remove(0));
        };
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
        PdfExportProperties props = new PdfExportProperties(
                new PdfExportProperties.Pdf("http://pdf-service:8080", 10000),
                "http://localhost:5173");
        return new PortfolioPdfService(builder, dataClient, forexHistoryClient, templateEngine, messageSource, svgService, props);
    }

    @BeforeEach
    void setUp() {
        lastRequest.set(null);
        queuedResponses.clear();
    }

    @Test
    void generate_sendsHtmlContentToPdfServiceAndReturnsBytes() throws Exception {
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(42L, null, "DARK", "tr", "TRY");
        when(dataClient.fetch(eq(42L), anyString(), eq("jwt.token")))
                .thenReturn(new PortfolioReportBundle(42L, null, List.of(), List.of(), List.of(stubPosition(42L)), List.of(), List.of()));
        when(svgService.performanceLineChart(any(), any(), any(Locale.class), anyString())).thenReturn("<svg/>");
        when(templateEngine.process(eq("pdf/portfolio-report"), any(Context.class)))
                .thenReturn("<html><body>report</body></html>");
        byte[] expected = new byte[]{37, 80, 68, 70, 1, 2, 3};
        queuedResponses.add(pdfResponse(expected));

        byte[] result = service.generate(req, "user-sub", "jwt.token");

        assertThat(result).isEqualTo(expected);
        ClientRequest recorded = lastRequest.get();
        assertThat(recorded.url().toString()).isEqualTo("http://pdf-service:8080/render");
        JsonNode body = mapper.readTree(extractBody(recorded));
        assertThat(body.get("htmlContent").asText()).contains("<body>report</body>");
        assertThat(body.has("url")).isFalse();
        assertThat(body.get("pdfOptions").get("format").asText()).isEqualTo("A4");
        assertThat(body.get("pdfOptions").get("printBackground").asBoolean()).isTrue();
    }

    @Test
    void generate_throwsWhenPdfServiceReturnsError() {
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(1L, null, "LIGHT", "en", "USD");
        when(dataClient.fetch(eq(1L), anyString(), anyString()))
                .thenReturn(new PortfolioReportBundle(1L, null, List.of(), List.of(), List.of(stubPosition(1L)), List.of(), List.of()));
        when(forexHistoryClient.fetchHistory(eq("USD"), anyString())).thenReturn(List.of());
        when(svgService.performanceLineChart(any(), any(), any(Locale.class), anyString())).thenReturn("");
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html/>");
        queuedResponses.add(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "text/plain")
                .body("boom").build());

        assertThatThrownBy(() -> service.generate(req, "sub", "tok"))
                .isInstanceOf(PdfGenerationException.class);
    }

    @Test
    void generate_throwsWhenPdfServiceReturnsEmptyBody() {
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(1L, null, "LIGHT", "en", "USD");
        when(dataClient.fetch(eq(1L), anyString(), anyString()))
                .thenReturn(new PortfolioReportBundle(1L, null, List.of(), List.of(), List.of(stubPosition(1L)), List.of(), List.of()));
        when(forexHistoryClient.fetchHistory(eq("USD"), anyString())).thenReturn(List.of());
        when(svgService.performanceLineChart(any(), any(), any(Locale.class), anyString())).thenReturn("");
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html/>");
        queuedResponses.add(pdfResponse(new byte[0]));

        assertThatThrownBy(() -> service.generate(req, "sub", "tok"))
                .isInstanceOf(PdfGenerationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generate_wrapsDataClientFailure() {
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(1L, null, "LIGHT", "tr", "TRY");
        when(dataClient.fetch(eq(1L), anyString(), anyString()))
                .thenThrow(new IllegalStateException("upstream down"));

        assertThatThrownBy(() -> service.generate(req, "sub", "tok"))
                .isInstanceOf(PdfGenerationException.class);
    }

    @Test
    void generate_rejectsEmptyPortfolio_withBadRequest() {
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(1L, null, "LIGHT", "tr", "TRY");
        when(dataClient.fetch(eq(1L), anyString(), anyString()))
                .thenReturn(new PortfolioReportBundle(1L, null, List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThatThrownBy(() -> service.generate(req, "sub", "tok"))
                .isInstanceOf(com.finance.common.exception.BadRequestException.class)
                .hasMessageContaining("error.report.portfolioEmpty");
    }

    @Test
    void generate_rejectsNullPositions_withBadRequest() {
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(2L, null, "LIGHT", "tr", "TRY");
        when(dataClient.fetch(eq(2L), anyString(), anyString()))
                .thenReturn(new PortfolioReportBundle(2L, null, List.of(), List.of(), null, List.of(), List.of()));

        assertThatThrownBy(() -> service.generate(req, "sub", "tok"))
                .isInstanceOf(com.finance.common.exception.BadRequestException.class);
    }


    @Test
    void generate_fallsBackToTry_whenForexHistoryEmptyForNonTryCurrency() {
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(7L, null, "DARK", "tr", "USD");
        when(dataClient.fetch(eq(7L), anyString(), anyString()))
                .thenReturn(new PortfolioReportBundle(7L, null, List.of(), List.of(), List.of(stubPosition(7L)), List.of(), List.of()));
        when(forexHistoryClient.fetchHistory(eq("USD"), anyString())).thenReturn(List.of());
        when(svgService.performanceLineChart(any(), any(), any(Locale.class), anyString())).thenReturn("<svg/>");
        ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("pdf/portfolio-report"), ctx.capture())).thenReturn("<html/>");
        queuedResponses.add(pdfResponse(new byte[]{1}));

        service.generate(req, "sub", "tok");

        assertThat(ctx.getValue().getVariable("currency")).isEqualTo("TRY");
        assertThat(ctx.getValue().getVariable("currencySymbol")).isEqualTo("₺");
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_convertsClosedLotAndRealizedPnl_atExitDateAndPerCurrencyFrame_notTodaySpot() {
        // Arrange: a closed lot with a 1000 TRY market value that exited on 2024-01-02, when the
        // USD selling rate was 20 ₺ (history forward-fills to 40 ₺ for today). The realized
        // allocation carries a USD frame of 7.5 alongside a 200 TRY scalar that would convert
        // differently at today's rate, so consuming the frame is observable.
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(9L, null, "DARK", "tr", "USD");
        LocalDateTime exit = LocalDate.of(2024, 1, 2).atStartOfDay();
        ReportPosition closedLot = new ReportPosition(
                9L, "STOCK", "ABC", "ABC", BigDecimal.ONE,
                LocalDate.of(2023, 1, 2).atStartOfDay(), new BigDecimal("100"),
                exit, new BigDecimal("1000"),
                new BigDecimal("1000"), new BigDecimal("100"),
                new BigDecimal("1000"), new BigDecimal("0"), BigDecimal.ZERO);
        ReportAllocation realized = new ReportAllocation(
                "Hisse", "STOCK", new BigDecimal("1000"), new BigDecimal("100"),
                new BigDecimal("180"), new BigDecimal("200"),
                Map.of("USD", new BigDecimal("7.5")), Map.of("USD", new BigDecimal("9")));
        when(dataClient.fetch(eq(9L), anyString(), anyString()))
                .thenReturn(new PortfolioReportBundle(9L, null, List.of(), List.of(realized),
                        List.of(closedLot), List.of(), List.of()));
        when(forexHistoryClient.fetchHistory(eq("USD"), anyString())).thenReturn(List.of(
                new ForexRatePoint(LocalDate.of(2024, 1, 2), new BigDecimal("20")),
                new ForexRatePoint(LocalDate.of(2025, 1, 2), new BigDecimal("40"))));
        when(svgService.allocationDonut(any(), any())).thenReturn("<svg/>");
        when(svgService.performanceLineChart(any(), any(), any(Locale.class), anyString())).thenReturn("<svg/>");
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Hisse");
        ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("pdf/portfolio-report"), ctx.capture())).thenReturn("<html/>");
        queuedResponses.add(pdfResponse(new byte[]{1}));

        // Act
        service.generate(req, "sub", "tok");

        // Assert: closed lot's USD market value uses the 2024-01-02 rate (1000 / 20 = 50), not
        // today's 40 ₺ rate (which would yield 25); realized winner uses the 7.5 USD frame.
        List<ReportPosition> positions = (List<ReportPosition>) ctx.getValue().getVariable("positions");
        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).marketValueTry()).isEqualByComparingTo("50");
        List<RealizedViewItem> winners = (List<RealizedViewItem>) ctx.getValue().getVariable("winners");
        assertThat(winners).hasSize(1);
        assertThat(winners.get(0).realized()).isEqualByComparingTo("7.5");
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_closedShortLotProfitableInTry_rendersPositivePnlInUsdReport() {
        // Arrange: a closed VİOP SHORT lot whose notional FELL from 1000 to 600 TRY — a profit for a
        // SHORT. marketValueTry (600) is direction-blind notional, so the raw (market − entry) is
        // negative; the direction sign must flip it positive. Entry@2024-01-02 (rate 20) and the
        // exit@2024-01-02 (rate 20) keep their per-date FX, so the USD P&L is +(1000 − 600)/20 = +20.
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(11L, null, "DARK", "tr", "USD");
        LocalDateTime entry = LocalDate.of(2024, 1, 2).atStartOfDay();
        LocalDateTime exit = LocalDate.of(2024, 1, 2).atStartOfDay();
        ReportPosition shortLot = new ReportPosition(
                11L, "VIOP", "F_XU0300424", "F_XU0300424 · KAPALI", BigDecimal.ONE,
                entry, new BigDecimal("1000"),
                exit, new BigDecimal("600"),
                new BigDecimal("600"), new BigDecimal("1000"),
                new BigDecimal("600"), new BigDecimal("400"), BigDecimal.ZERO,
                "SHORT");
        when(dataClient.fetch(eq(11L), anyString(), anyString()))
                .thenReturn(new PortfolioReportBundle(11L, null, List.of(), List.of(),
                        List.of(shortLot), List.of(), List.of()));
        when(forexHistoryClient.fetchHistory(eq("USD"), anyString())).thenReturn(List.of(
                new ForexRatePoint(LocalDate.of(2024, 1, 2), new BigDecimal("20"))));
        when(svgService.allocationDonut(any(), any())).thenReturn("<svg/>");
        when(svgService.performanceLineChart(any(), any(), any(Locale.class), anyString())).thenReturn("<svg/>");
        ArgumentCaptor<Context> ctx = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("pdf/portfolio-report"), ctx.capture())).thenReturn("<html/>");
        queuedResponses.add(pdfResponse(new byte[]{1}));

        // Act
        service.generate(req, "sub", "tok");

        // Assert: the converted P&L (carried in the rebuilt position's pnlTry slot) is POSITIVE.
        List<ReportPosition> positions = (List<ReportPosition>) ctx.getValue().getVariable("positions");
        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).pnlTry()).isEqualByComparingTo("20");
    }

    private static ReportPosition stubPosition(long id) {
        return new ReportPosition(id, "STOCK", "X", "X", null, null, null, null, null, null, null, null, null, null);
    }

    private ClientResponse pdfResponse(byte[] bytes) {
        DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
        DataBuffer buffer = factory.wrap(bytes);
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_PDF_VALUE)
                .body(Flux.just(buffer))
                .build();
    }

    private String extractBody(ClientRequest req) {
        MockClientHttpRequest mock = new MockClientHttpRequest(req.method(), req.url());
        ExchangeStrategies strategies = ExchangeStrategies.withDefaults();
        BodyInserter.Context ctx = new BodyInserter.Context() {
            @Override
            public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                return strategies.messageWriters();
            }

            @Override
            public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public java.util.Map<String, Object> hints() {
                return java.util.Map.of();
            }
        };
        req.body().insert(mock, ctx).block();
        ByteBuffer collected = mock.getBody()
                .map(DataBuffer::toByteBuffer)
                .reduce(ByteBuffer.allocate(0), (acc, next) -> {
                    ByteBuffer out = ByteBuffer.allocate(acc.remaining() + next.remaining());
                    out.put(acc).put(next).flip();
                    return out;
                })
                .block();
        if (collected == null) return "";
        byte[] arr = new byte[collected.remaining()];
        collected.get(arr);
        return new String(arr, StandardCharsets.UTF_8);
    }
}
