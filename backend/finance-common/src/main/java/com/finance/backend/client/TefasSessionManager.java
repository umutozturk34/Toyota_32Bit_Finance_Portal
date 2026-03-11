package com.finance.backend.client;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Log4j2
@Component
public class TefasSessionManager {

    private final WebClient webClient;
    private volatile String sessionCookie;

    public TefasSessionManager(@Qualifier("tefasBaseWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public String getCookie() {
        if (sessionCookie == null) {
            refresh();
        }
        return sessionCookie;
    }

    public synchronized void refresh() {
        try {
            webClient.get()
                    .uri("/TarihselVeriler.aspx")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .exchangeToMono(response -> {
                        List<String> cookies = response.headers().header("Set-Cookie");
                        if (!cookies.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (String c : cookies) {
                                String cookiePart = c.split(";")[0];
                                if (!sb.isEmpty()) sb.append("; ");
                                sb.append(cookiePart);
                            }
                            sessionCookie = sb.toString();
                            log.info("TEFAS session refreshed: {} chars", sessionCookie.length());
                        } else {
                            log.warn("TEFAS session refresh returned no cookies");
                        }
                        return response.releaseBody();
                    })
                    .block();
        } catch (Exception e) {
            log.warn("Failed to refresh TEFAS session: {}", e.getMessage());
        }
    }

    public void invalidate() {
        sessionCookie = null;
    }
}
