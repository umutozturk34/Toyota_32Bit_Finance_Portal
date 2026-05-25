package com.finance.notification.reports.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.notification.config.PdfExportProperties;
import com.finance.notification.reports.client.PortfolioDataClient;
import com.finance.notification.reports.dto.PortfolioPdfRequest;
import com.finance.notification.reports.dto.PortfolioReportBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        return new PortfolioPdfService(builder, dataClient, templateEngine, messageSource, svgService, props);
    }

    @BeforeEach
    void setUp() {
        lastRequest.set(null);
        queuedResponses.clear();
    }

    @Test
    void generate_sendsHtmlContentToPdfServiceAndReturnsBytes() throws Exception {
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(42L, "DARK", "tr", "TRY");
        when(dataClient.fetch(eq(42L), anyString(), eq("jwt.token")))
                .thenReturn(new PortfolioReportBundle(42L, null, List.of(), List.of(), List.of()));
        when(svgService.performanceLineChart(any(), any(), any(Locale.class))).thenReturn("<svg/>");
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
        PortfolioPdfRequest req = new PortfolioPdfRequest(1L, "LIGHT", "en", "USD");
        when(dataClient.fetch(eq(1L), anyString(), anyString()))
                .thenReturn(new PortfolioReportBundle(1L, null, List.of(), List.of(), List.of()));
        when(svgService.performanceLineChart(any(), any(), any(Locale.class))).thenReturn("");
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
        PortfolioPdfRequest req = new PortfolioPdfRequest(1L, "LIGHT", "en", "USD");
        when(dataClient.fetch(eq(1L), anyString(), anyString()))
                .thenReturn(new PortfolioReportBundle(1L, null, List.of(), List.of(), List.of()));
        when(svgService.performanceLineChart(any(), any(), any(Locale.class))).thenReturn("");
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html/>");
        queuedResponses.add(pdfResponse(new byte[0]));

        assertThatThrownBy(() -> service.generate(req, "sub", "tok"))
                .isInstanceOf(PdfGenerationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void generate_wrapsDataClientFailure() {
        PortfolioPdfService service = buildService();
        PortfolioPdfRequest req = new PortfolioPdfRequest(1L, "LIGHT", "tr", "TRY");
        when(dataClient.fetch(eq(1L), anyString(), anyString()))
                .thenThrow(new IllegalStateException("upstream down"));

        assertThatThrownBy(() -> service.generate(req, "sub", "tok"))
                .isInstanceOf(PdfGenerationException.class);
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
