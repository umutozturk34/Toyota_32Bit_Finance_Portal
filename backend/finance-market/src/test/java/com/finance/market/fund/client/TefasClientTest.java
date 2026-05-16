package com.finance.market.fund.client;

import com.finance.common.config.AppProperties;
import com.finance.common.exception.ExternalApiException;
import com.finance.market.core.filter.TefasSessionManager;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.dto.external.TefasFundAllocationDto;
import com.finance.market.fund.dto.external.TefasFundInfoDto;
import com.finance.market.fund.dto.external.TefasFundProfileDto;
import com.finance.market.fund.dto.external.TefasFundReturnsDto;
import com.finance.market.fund.mapper.TefasClientMapper;
import com.finance.market.fund.model.FundType;
import com.finance.shared.exception.ExternalApiRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TefasClientTest {

    @Mock private TefasClientMapper tefasClientMapper;
    @Mock private TefasSessionManager sessionManager;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
    }

    private WebClient stubWebClient(ExchangeFunction exchange) {
        return WebClient.builder().exchangeFunction(exchange).build();
    }

    private ExchangeFunction respondBody(String body) {
        return request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private TefasClient buildClient(WebClient webClient) {
        AppProperties appProperties = new AppProperties();
        appProperties.setTefasApiPath("/api/funds/BindHistoryAllocation");
        appProperties.setTefasReturnsPath("/api/funds/returns");
        appProperties.setTefasAllocationPath("/api/funds/allocation");
        appProperties.setTefasInfoPath("/api/funds/info");
        appProperties.setTefasProfilePath("/api/funds/profile");
        FundProperties fundProperties = new FundProperties();
        fundProperties.setTefasBulkPageSize(100_000);
        fundProperties.setTefasDefaultPageSize(100);
        fundProperties.setTefasLanguage("TR");
        return new TefasClient(webClient, appProperties, fundProperties, objectMapper,
                tefasClientMapper, sessionManager);
    }

    @Test
    void should_returnReturnsList_when_fetchReturnsHasResultList() {
        String body = "{\"errorCode\":null,\"errorMessage\":null,\"resultList\":["
                + "{\"fonKodu\":\"AAA\",\"fonUnvan\":\"Fund A\",\"fonTurAciklama\":\"Equity\","
                + "\"getiri1a\":1.5,\"getiri1y\":12.0,\"riskDegeri\":\"4\"}"
                + "]}";
        TefasClient client = buildClient(stubWebClient(respondBody(body)));

        List<TefasFundReturnsDto> result = client.fetchReturns(FundType.YAT);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fundCode()).isEqualTo("AAA");
        assertThat(result.get(0).return1m()).isEqualByComparingTo("1.5");
        assertThat(result.get(0).riskValue()).isEqualTo("4");
    }

    @Test
    void should_returnEmptyList_when_fetchReturnsResultListIsNull() {
        String body = "{\"errorCode\":null,\"errorMessage\":null,\"resultList\":null}";
        TefasClient client = buildClient(stubWebClient(respondBody(body)));

        List<TefasFundReturnsDto> result = client.fetchReturns(FundType.YAT);

        assertThat(result).isEmpty();
    }

    @Test
    void should_throwExternalApiRequestException_when_fetchReturnsBodyStartsWithAngleBracket() {
        TefasClient client = buildClient(stubWebClient(respondBody("<html>WAF</html>")));

        assertThatThrownBy(() -> client.fetchReturns(FundType.YAT))
                .isInstanceOf(ExternalApiRequestException.class)
                .hasMessageContaining("WAF blocked");
    }

    @Test
    void should_invalidateSessionAndThrowExternalApiException_when_fetchReturnsBodyIsEmpty() {
        TefasClient client = buildClient(stubWebClient(respondBody("")));

        assertThatThrownBy(() -> client.fetchReturns(FundType.YAT))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Empty response");
        verify(sessionManager).invalidate();
    }

    @Test
    void should_throwExternalApiException_when_fetchReturnsResponseHasErrorCode() {
        String body = "{\"errorCode\":\"E1\",\"errorMessage\":\"boom\",\"resultList\":[]}";
        TefasClient client = buildClient(stubWebClient(respondBody(body)));

        assertThatThrownBy(() -> client.fetchReturns(FundType.YAT))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("TEFAS error E1");
    }

    @Test
    void should_returnAllocationList_when_fetchAllocationsHasResultList() {
        String body = "{\"errorCode\":null,\"errorMessage\":null,\"resultList\":["
                + "{\"fonKodu\":\"AAA\",\"fonUnvan\":\"Fund A\",\"tarih\":\"20260501\","
                + "\"hisseSenedi\":40.5,\"borc\":20.0,\"bilFiyat\":12.34}"
                + "]}";
        TefasClient client = buildClient(stubWebClient(respondBody(body)));

        List<TefasFundAllocationDto> result = client.fetchAllocations(FundType.YAT, LocalDate.of(2026, 5, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fundCode()).isEqualTo("AAA");
        assertThat(result.get(0).allocations()).containsKeys("hisseSenedi", "borc");
        assertThat(result.get(0).allocations()).doesNotContainKey("bilFiyat");
    }

    @Test
    void should_throwExternalApiRequestException_when_fetchAllocationsBodyStartsWithAngleBracket() {
        TefasClient client = buildClient(stubWebClient(respondBody("<html>WAF</html>")));

        assertThatThrownBy(() -> client.fetchAllocations(FundType.YAT, LocalDate.of(2026, 5, 1)))
                .isInstanceOf(ExternalApiRequestException.class)
                .hasMessageContaining("WAF blocked");
    }

    @Test
    void should_returnFirstInfoDto_when_fetchInfoResultListNonEmpty() {
        String body = "{\"errorCode\":null,\"errorMessage\":null,\"resultList\":["
                + "{\"fonKodu\":\"AAA\",\"fonUnvan\":\"Fund A\",\"sonFiyat\":1.25,\"fonKategori\":\"Equity\"}"
                + "]}";
        TefasClient client = buildClient(stubWebClient(respondBody(body)));

        TefasFundInfoDto result = client.fetchInfo(FundType.YAT, "AAA");

        assertThat(result).isNotNull();
        assertThat(result.fundCode()).isEqualTo("AAA");
        assertThat(result.lastPrice()).isEqualByComparingTo("1.25");
        assertThat(result.category()).isEqualTo("Equity");
    }

    @Test
    void should_returnNull_when_fetchInfoResultListEmpty() {
        String body = "{\"errorCode\":null,\"errorMessage\":null,\"resultList\":[]}";
        TefasClient client = buildClient(stubWebClient(respondBody(body)));

        TefasFundInfoDto result = client.fetchInfo(FundType.YAT, "AAA");

        assertThat(result).isNull();
    }

    @Test
    void should_invalidateSessionAndThrow_when_fetchInfoBodyEmpty() {
        TefasClient client = buildClient(stubWebClient(respondBody("")));

        assertThatThrownBy(() -> client.fetchInfo(FundType.YAT, "AAA"))
                .isInstanceOf(ExternalApiException.class);
        verify(sessionManager).invalidate();
    }

    @Test
    void should_returnFirstProfileDto_when_fetchProfileResultListNonEmpty() {
        String body = "{\"errorCode\":null,\"errorMessage\":null,\"resultList\":["
                + "{\"fonKodu\":\"AAA\",\"fonUnvan\":\"Fund A\",\"isinKodu\":\"TR123\","
                + "\"riskDegeri\":\"4\",\"tefasDurum\":\"OPEN\"}"
                + "]}";
        TefasClient client = buildClient(stubWebClient(respondBody(body)));

        TefasFundProfileDto result = client.fetchProfile(FundType.YAT, "AAA");

        assertThat(result).isNotNull();
        assertThat(result.fundCode()).isEqualTo("AAA");
        assertThat(result.isinCode()).isEqualTo("TR123");
        assertThat(result.riskValue()).isEqualTo("4");
    }

    @Test
    void should_returnNull_when_fetchProfileResultListEmpty() {
        String body = "{\"errorCode\":null,\"errorMessage\":null,\"resultList\":[]}";
        TefasClient client = buildClient(stubWebClient(respondBody(body)));

        TefasFundProfileDto result = client.fetchProfile(FundType.YAT, "AAA");

        assertThat(result).isNull();
    }

    @Test
    void should_throwExternalApiException_when_fetchProfileResponseHasErrorCode() {
        String body = "{\"errorCode\":\"E9\",\"errorMessage\":\"bad\",\"resultList\":[]}";
        TefasClient client = buildClient(stubWebClient(respondBody(body)));

        assertThatThrownBy(() -> client.fetchProfile(FundType.YAT, "AAA"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("TEFAS error E9");
    }
}
