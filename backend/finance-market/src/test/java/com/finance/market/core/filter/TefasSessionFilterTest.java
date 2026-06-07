package com.finance.market.core.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TefasSessionFilterTest {

    @Mock private TefasSessionManager sessionManager;
    @Mock private ExchangeFunction next;
    @Mock private ClientResponse response;

    private TefasSessionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TefasSessionFilter(sessionManager);
        when(next.exchange(any())).thenReturn(Mono.just(response));
    }

    @Test
    void should_attachSessionCookie_when_requestIsNotSessionPath() {
        when(sessionManager.isSessionPath("/api/DB/BindHistoryInfo")).thenReturn(false);
        when(sessionManager.getCookie()).thenReturn("ASP.NET_SessionId=abc123");
        ClientRequest request = ClientRequest
                .create(HttpMethod.GET, URI.create("https://www.tefas.gov.tr/api/DB/BindHistoryInfo"))
                .build();

        filter.filter(request, next).block();

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(next).exchange(captor.capture());
        assertThat(captor.getValue().headers().getFirst("Cookie")).isEqualTo("ASP.NET_SessionId=abc123");
    }

    @Test
    void should_passRequestUntouched_when_requestIsSessionWarmUpPath() {
        when(sessionManager.isSessionPath("/")).thenReturn(true);
        ClientRequest request = ClientRequest
                .create(HttpMethod.GET, URI.create("https://www.tefas.gov.tr/"))
                .build();

        filter.filter(request, next).block();

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(next).exchange(captor.capture());
        assertThat(captor.getValue().headers().getFirst("Cookie")).isNull();
        verify(sessionManager, never()).getCookie();
    }
}
