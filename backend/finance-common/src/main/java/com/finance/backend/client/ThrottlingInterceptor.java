package com.finance.backend.client;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
public class ThrottlingInterceptor implements ClientHttpRequestInterceptor {
    private final long minIntervalMs;
    private volatile long lastRequestTimeMs;
    public ThrottlingInterceptor(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }
    @Override
    public synchronized ClientHttpResponse intercept(
            HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        long elapsed = System.currentTimeMillis() - lastRequestTimeMs;
        if (elapsed < minIntervalMs) {
            try {
                TimeUnit.MILLISECONDS.sleep(minIntervalMs - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTimeMs = System.currentTimeMillis();
        return execution.execute(request, body);
    }
}
