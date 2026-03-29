package com.finance.backend.client;

import com.finance.backend.exception.ExternalApiException;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;

@Component
@Log4j2
public class RssClient {

    private final WebClient webClient;

    public RssClient(@Qualifier("newsWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @CircuitBreaker(name = "news")
    @Retry(name = "news")
    public SyndFeed fetchFeed(String feedUrl) {
        log.debug("Fetching RSS feed: {}", feedUrl);

        byte[] bytes = webClient.get()
                .uri(feedUrl)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

        if (bytes == null || bytes.length == 0) {
            throw new ExternalApiException("RSS", "Empty response from feed: " + feedUrl);
        }

        log.debug("RSS feed fetched: {} bytes from {}", bytes.length, feedUrl);
        return parseFeed(bytes, feedUrl);
    }

    private SyndFeed parseFeed(byte[] bytes, String feedUrl) {
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(bytes))) {
            return new SyndFeedInput().build(reader);
        } catch (Exception e) {
            throw new ExternalApiException("RSS", "Failed to parse feed: " + feedUrl, e);
        }
    }
}
