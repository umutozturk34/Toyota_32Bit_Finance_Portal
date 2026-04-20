package com.finance.backend.filter;

import com.finance.backend.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TefasSessionManagerTest {

    private RecordingSessionManager manager;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setTefasSessionPath("/session");
        manager = new RecordingSessionManager(properties);
    }

    @Test
    void firstGetCookieTriggersExactlyOneFetch() {
        String cookie = manager.getCookie();

        assertThat(cookie).isEqualTo("cookie-1");
        assertThat(manager.fetchCount()).isEqualTo(1);
    }

    @Test
    void subsequentGetCookieReturnsCachedValueWithoutFetching() {
        manager.getCookie();
        int firstCount = manager.fetchCount();

        String second = manager.getCookie();
        String third = manager.getCookie();

        assertThat(second).isEqualTo("cookie-1");
        assertThat(third).isEqualTo("cookie-1");
        assertThat(manager.fetchCount()).isEqualTo(firstCount);
    }

    @Test
    void invalidateClearsCookieSoNextGetCookieTriggersFetch() {
        manager.getCookie();

        manager.invalidate();
        String refreshed = manager.getCookie();

        assertThat(refreshed).isEqualTo("cookie-2");
        assertThat(manager.fetchCount()).isEqualTo(2);
    }

    @Test
    void refreshAlwaysFetchesEvenIfCookieIsPresent() {
        manager.getCookie();

        manager.refresh();

        assertThat(manager.getCookie()).isEqualTo("cookie-2");
        assertThat(manager.fetchCount()).isEqualTo(2);
    }

    @Test
    void concurrentGetCookieCallsCollapseIntoSingleFetch() throws InterruptedException {
        int threads = 32;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        manager.getCookie();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(manager.fetchCount()).isEqualTo(1);
    }

    private static final class RecordingSessionManager extends TefasSessionManager {
        private final AtomicInteger fetchCount = new AtomicInteger();

        RecordingSessionManager(AppProperties properties) {
            super(null, properties);
        }

        @Override
        protected void fetchCookie() {
            int invocation = fetchCount.incrementAndGet();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            setSessionCookie("cookie-" + invocation);
        }

        int fetchCount() {
            return fetchCount.get();
        }
    }
}
