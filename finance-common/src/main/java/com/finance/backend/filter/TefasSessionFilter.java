package com.finance.backend.filter;

import lombok.extern.log4j.Log4j2;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import reactor.core.publisher.Mono;

@Log4j2
public class TefasSessionFilter implements ExchangeFilterFunction {

    private final TefasSessionManager sessionManager;

    public TefasSessionFilter(TefasSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        if (isSessionRequest(request)) {
            return next.exchange(request);
        }

        String cookie = sessionManager.getCookie();
        ClientRequest authenticatedRequest = ClientRequest.from(request)
                .header("Cookie", cookie)
                .build();
        return next.exchange(authenticatedRequest);
    }

    private boolean isSessionRequest(ClientRequest request) {
        return sessionManager.isSessionPath(request.url().getPath());
    }
}
