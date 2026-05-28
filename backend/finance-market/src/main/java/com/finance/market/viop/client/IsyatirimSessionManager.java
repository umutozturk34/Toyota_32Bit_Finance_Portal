package com.finance.market.viop.client;

import com.finance.market.viop.config.ViopProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maintains the İş Yatırım session cookie used for VIOP requests: warms it up from the analysis
 * page, serves a header string, and refreshes when stale (TTL-based) or forced after an auth
 * rejection. Warm-up failures degrade gracefully to a cookieless state.
 */
@Log4j2
@Component
public class IsyatirimSessionManager {

    private final WebClient viopWebClient;
    private final ViopProperties properties;
    private final AtomicReference<CookieState> state = new AtomicReference<>(CookieState.empty());

    public IsyatirimSessionManager(@Qualifier("viopWebClient") WebClient viopWebClient,
                                   ViopProperties properties) {
        this.viopWebClient = viopWebClient;
        this.properties = properties;
    }

    /** Current cookie header, transparently refreshing the session if it has gone stale. */
    public String currentCookieHeader() {
        CookieState s = state.get();
        if (s.isStale(properties.sessionTtl())) {
            refresh();
            s = state.get();
        }
        return s.cookieHeader;
    }

    /** Discards the current cookies and re-warms the session (called after an auth rejection). */
    public void forceRefresh() {
        log.info("Force-refreshing İş Yatırım VIOP session cookies");
        state.set(CookieState.empty());
        refresh();
    }

    private synchronized void refresh() {
        CookieState s = state.get();
        if (!s.isStale(properties.sessionTtl())) {
            return;
        }
        try {
            ResponseEntity<String> response = viopWebClient.get()
                    .uri(properties.viopAnalysisPagePath())
                    .retrieve()
                    .toEntity(String.class)
                    .block(properties.requestTimeout());
            String cookieHeader = response == null
                    ? ""
                    : buildCookieHeader(response.getHeaders().get(HttpHeaders.SET_COOKIE));
            state.set(new CookieState(cookieHeader, Instant.now()));
            if (cookieHeader.isEmpty()) {
                log.debug("İş Yatırım VIOP warm-up returned no Set-Cookie; proceeding cookieless");
            } else {
                log.info("İş Yatırım VIOP session warmed up; {} cookie(s) captured",
                        cookieHeader.split(";").length);
            }
        } catch (Exception e) {
            log.warn("İş Yatırım VIOP session warm-up failed ({}); proceeding cookieless", e.getMessage());
            state.set(new CookieState("", Instant.now()));
        }
    }

    private String buildCookieHeader(List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String raw : setCookies) {
            int semi = raw.indexOf(';');
            String kv = semi >= 0 ? raw.substring(0, semi) : raw;
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(kv.trim());
        }
        return sb.toString();
    }

    private record CookieState(String cookieHeader, Instant acquiredAt) {
        static CookieState empty() {
            return new CookieState("", Instant.EPOCH);
        }

        boolean isStale(Duration ttl) {
            return acquiredAt.equals(Instant.EPOCH) || Instant.now().isAfter(acquiredAt.plus(ttl));
        }
    }
}
