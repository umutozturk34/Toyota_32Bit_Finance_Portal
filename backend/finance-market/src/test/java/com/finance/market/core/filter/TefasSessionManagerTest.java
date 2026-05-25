package com.finance.market.core.filter;

import com.finance.common.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TefasSessionManagerTest {

    private AppProperties appProperties;
    private WebClient webClient;
    private AtomicInteger fetchCount;
    private TefasSessionManager manager;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.setTefasSessionPath("/session");
        webClient = WebClient.builder().baseUrl("http://localhost").build();
        fetchCount = new AtomicInteger();
        manager = new TefasSessionManager(webClient, appProperties) {
            @Override
            protected void fetchCookie() {
                fetchCount.incrementAndGet();
                setSessionCookie("cookie=" + fetchCount.get());
            }
        };
    }

    @Test
    void getCookie_fetchesOnce_andReturnsCachedSnapshot() {
        String first = manager.getCookie();
        String second = manager.getCookie();

        assertThat(first).isEqualTo("cookie=1");
        assertThat(second).isEqualTo("cookie=1");
        assertThat(fetchCount).hasValue(1);
    }

    @Test
    void getCookie_refetches_afterInvalidate() {
        manager.getCookie();
        manager.invalidate();

        String after = manager.getCookie();

        assertThat(after).isEqualTo("cookie=2");
        assertThat(fetchCount).hasValue(2);
    }

    @Test
    void refresh_alwaysFetches_evenWhenCookieAlreadyPresent() {
        manager.getCookie();

        manager.refresh();

        assertThat(fetchCount).hasValue(2);
    }

    @Test
    void isSessionPath_returnsTrue_forConfiguredPath() {
        assertThat(manager.isSessionPath("/session")).isTrue();
    }

    @Test
    void isSessionPath_returnsFalse_forOtherPath() {
        assertThat(manager.isSessionPath("/other")).isFalse();
    }

    @Test
    void getCookie_returnsNullCookie_whenFetcherProducesNoValue() {
        TefasSessionManager nullFetcher = new TefasSessionManager(webClient, appProperties) {
            @Override
            protected void fetchCookie() {
            }
        };

        String result = nullFetcher.getCookie();

        assertThat(result).isNull();
    }

    @Test
    void invalidate_clearsCachedCookie_priorToNextGetCall() {
        manager.getCookie();
        manager.invalidate();

        manager.getCookie();

        assertThat(fetchCount).hasValue(2);
    }

    @Test
    void fetchCookie_isSilent_whenWebClientThrows() {
        WebClient broken = WebClient.builder().baseUrl("http://invalid.host.localdomain").build();
        TefasSessionManager real = new TefasSessionManager(broken, appProperties);

        real.refresh();

        assertThat(real.getCookie()).isNull();
    }
}
