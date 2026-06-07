package com.finance.news.mapper;

import com.finance.news.dto.response.NewsArticleDetailResponse;
import com.finance.news.dto.response.NewsArticleResponse;
import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsCategory;
import com.finance.news.model.NewsSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsResponseMapperTest {

    private NewsResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new NewsResponseMapperImpl();
    }

    private NewsArticle article() {
        return NewsArticle.builder()
                .id(7L)
                .title("BTC rallies")
                .link("https://x/btc")
                .description("crypto desc")
                .content("full body")
                .imageUrl("https://x/img.png")
                .category(NewsCategory.CRYPTO)
                .publishedAt(LocalDateTime.of(2026, 6, 1, 8, 0))
                .source(NewsSource.builder().name("CoinDesk").build())
                .build();
    }

    @Test
    void should_mapSummaryFieldsAndCategoryName_when_toResponse() {
        // Arrange
        NewsArticle article = article();

        // Act
        NewsArticleResponse response = mapper.toResponse(article);

        // Assert
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("BTC rallies");
        assertThat(response.sourceName()).isEqualTo("CoinDesk");
        assertThat(response.category()).isEqualTo("CRYPTO");
        assertThat(response.imageUrl()).isEqualTo("https://x/img.png");
    }

    @Test
    void should_mapBodyAndLink_when_toDetailResponse() {
        // Arrange
        NewsArticle article = article();

        // Act
        NewsArticleDetailResponse response = mapper.toDetailResponse(article);

        // Assert
        assertThat(response.link()).isEqualTo("https://x/btc");
        assertThat(response.content()).isEqualTo("full body");
        assertThat(response.category()).isEqualTo("CRYPTO");
    }

    @Test
    void should_returnNull_when_toResponseGivenNull() {
        // Arrange + Act
        NewsArticleResponse response = mapper.toResponse(null);

        // Assert
        assertThat(response).isNull();
    }

    @Test
    void should_returnNull_when_toDetailResponseGivenNull() {
        // Arrange + Act
        NewsArticleDetailResponse response = mapper.toDetailResponse(null);

        // Assert
        assertThat(response).isNull();
    }

    @Test
    void should_mapEachElement_when_toResponses() {
        // Arrange
        List<NewsArticle> articles = List.of(article());

        // Act
        List<NewsArticleResponse> responses = mapper.toResponses(articles);

        // Assert
        assertThat(responses).singleElement()
                .satisfies(r -> assertThat(r.title()).isEqualTo("BTC rallies"));
    }

    @Test
    void should_returnNull_when_toResponsesGivenNull() {
        // Arrange + Act
        List<NewsArticleResponse> responses = mapper.toResponses(null);

        // Assert
        assertThat(responses).isNull();
    }
}
