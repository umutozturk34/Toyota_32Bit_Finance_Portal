package com.finance.news.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsArticleTest {

    @Test
    void getSourceName_returnsNull_whenSourceUnset() {
        NewsArticle article = NewsArticle.builder().title("t").link("l").build();

        assertThat(article.getSourceName()).isNull();
    }

    @Test
    void getSourceName_returnsAttachedSourceFields() {
        NewsSource source = NewsSource.builder().name("Reuters").url("https://x").build();
        NewsArticle article = NewsArticle.builder().source(source).build();

        assertThat(article.getSourceName()).isEqualTo("Reuters");
    }
}
