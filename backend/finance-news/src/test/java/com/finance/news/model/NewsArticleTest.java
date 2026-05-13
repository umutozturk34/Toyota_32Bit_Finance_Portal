package com.finance.news.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NewsArticleTest {

    @Test
    void getSourceName_returnsNull_whenSourceUnset() {
        NewsArticle article = NewsArticle.builder().title("t").link("l").build();

        assertThat(article.getSourceName()).isNull();
        assertThat(article.getSourceUrl()).isNull();
    }

    @Test
    void getSourceName_returnsAttachedSourceFields() {
        NewsSource source = NewsSource.builder().name("Reuters").url("https://x").build();
        NewsArticle article = NewsArticle.builder().source(source).build();

        assertThat(article.getSourceName()).isEqualTo("Reuters");
        assertThat(article.getSourceUrl()).isEqualTo("https://x");
    }

    @Test
    void resolveCategory_setsCategoryFromContent() {
        NewsArticle article = NewsArticle.builder()
                .title("Bitcoin yükseldi")
                .description("kripto piyasası coştu")
                .build();

        article.resolveCategory("WORLD");

        assertThat(article.getCategory()).isNotNull();
    }

    @Test
    void isStale_returnsTrue_whenPublishedAtNull() {
        NewsArticle article = NewsArticle.builder().build();

        assertThat(article.isStale(12)).isTrue();
    }

    @Test
    void isStale_returnsFalse_whenPublishedRecently() {
        NewsArticle article = NewsArticle.builder()
                .publishedAt(LocalDateTime.now().minusHours(1))
                .build();

        assertThat(article.isStale(12)).isFalse();
    }

    @Test
    void isStale_returnsTrue_whenOlderThanThreshold() {
        NewsArticle article = NewsArticle.builder()
                .publishedAt(LocalDateTime.now().minusHours(48))
                .build();

        assertThat(article.isStale(12)).isTrue();
    }

    @Test
    void ageInHours_returnsMinusOne_whenPublishedAtNull() {
        NewsArticle article = NewsArticle.builder().build();

        assertThat(article.ageInHours()).isEqualTo(-1);
    }

    @Test
    void ageInHours_returnsPositiveDelta_whenSet() {
        NewsArticle article = NewsArticle.builder()
                .publishedAt(LocalDateTime.now().minusHours(5))
                .build();

        assertThat(article.ageInHours()).isBetween(4L, 6L);
    }
}
