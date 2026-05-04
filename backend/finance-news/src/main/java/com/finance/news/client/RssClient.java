package com.finance.news.client;
import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.news.dto.internal.RssArticleData;
import com.finance.common.exception.ExternalApiException;
import com.finance.news.mapper.RssClientMapper;
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
import java.util.List;

@Component
@Log4j2
public class RssClient {

    private final WebClient webClient;
    private final RssClientMapper rssClientMapper;

    public RssClient(@Qualifier("newsWebClient") WebClient webClient,
                     RssClientMapper rssClientMapper) {
        this.webClient = webClient;
        this.rssClientMapper = rssClientMapper;
    }

    @CircuitBreaker(name = "news")
    @Retry(name = "news")
    public List<RssArticleData> fetchFeed(String feedUrl) {
        log.debug("Fetching RSS feed: {}", feedUrl);
        try {
            byte[] bytes = webClient.get()
                    .uri(feedUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (bytes == null || bytes.length == 0) {
                throw new ExternalApiException("RSS", "Empty response from feed: " + feedUrl);
            }

            SyndFeed feed = parseFeed(bytes, feedUrl);
            List<RssArticleData> articles = rssClientMapper.toArticleDataList(feed);
            log.info("RSS feed fetched: {} articles from {}", articles.size(), feedUrl);
            return articles;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("RSS", "Failed to fetch feed: " + feedUrl, e);
        }
    }

    private SyndFeed parseFeed(byte[] bytes, String feedUrl) {
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(bytes))) {
            return new SyndFeedInput().build(reader);
        } catch (Exception e) {
            throw new ExternalApiException("RSS", "Failed to parse XML from feed: " + feedUrl, e);
        }
    }
}
