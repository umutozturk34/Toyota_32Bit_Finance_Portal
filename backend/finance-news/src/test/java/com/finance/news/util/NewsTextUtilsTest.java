package com.finance.news.util;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsTextUtilsTest {

    @Test
    void stripHtmlTagsRemovesAllTags() {
        String result = NewsTextUtils.stripHtmlTags("<p>Hello <b>World</b></p>");

        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    void stripHtmlTagsNullReturnsNull() {
        assertThat(NewsTextUtils.stripHtmlTags(null)).isNull();
    }

    @Test
    void stripHtmlTagsEmptyReturnsEmpty() {
        assertThat(NewsTextUtils.stripHtmlTags("")).isEmpty();
    }

    @Test
    void extractFirstImageUrlFindsFirstImgSrc() {
        String html = "<p>Text</p><img src=\"https://example.com/photo.jpg\" /><img src=\"second.jpg\" />";

        String result = NewsTextUtils.extractFirstImageUrl(html);

        assertThat(result).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void extractFirstImageUrlNullReturnsNull() {
        assertThat(NewsTextUtils.extractFirstImageUrl(null)).isNull();
    }

    @Test
    void extractFirstImageUrlEmptyReturnsNull() {
        assertThat(NewsTextUtils.extractFirstImageUrl("")).isNull();
    }

    @Test
    void extractFirstImageUrlNoImgTagReturnsNull() {
        assertThat(NewsTextUtils.extractFirstImageUrl("<p>No images here</p>")).isNull();
    }

    @Test
    void extractFirstImageUrlEmptySrcReturnsNull() {
        assertThat(NewsTextUtils.extractFirstImageUrl("<img src=\"\" />")).isNull();
    }

    @Test
    void stripCoverImageRemovesMatchingImgTag() {
        String cover = "https://example.com/article-header.jpg";
        String content = "<p>Text</p><img src=\"https://example.com/article-header.jpg\" /><p>More</p>";

        String result = NewsTextUtils.stripCoverImageFromContent(content, cover);

        assertThat(result).doesNotContain("<img");
        assertThat(result).contains("Text");
        assertThat(result).contains("More");
    }

    @Test
    void stripCoverImageRemovesMatchingFigureBlock() {
        String cover = "https://example.com/article-header.jpg";
        String content = "<figure><img src=\"https://example.com/article-header.jpg\" /></figure><p>Content</p>";

        String result = NewsTextUtils.stripCoverImageFromContent(content, cover);

        assertThat(result).doesNotContain("<figure");
        assertThat(result).contains("Content");
    }

    @Test
    void stripCoverImageMatchesWithQueryStringDifference() {
        String cover = "https://example.com/article-header.jpg?w=800";
        String content = "<img src=\"https://example.com/article-header.jpg?w=400\" /><p>Text</p>";

        String result = NewsTextUtils.stripCoverImageFromContent(content, cover);

        assertThat(result).doesNotContain("<img");
    }

    @Test
    void stripCoverImageMatchesResizeSuffix() {
        String cover = "https://example.com/article-header-800x600.jpg";
        String content = "<img src=\"https://example.com/article-header-400x300.jpg\" /><p>Text</p>";

        String result = NewsTextUtils.stripCoverImageFromContent(content, cover);

        assertThat(result).doesNotContain("<img");
    }

    @Test
    void stripCoverImageNullContentReturnsContent() {
        assertThat(NewsTextUtils.stripCoverImageFromContent(null, "url")).isNull();
    }

    @Test
    void stripCoverImageNullCoverReturnsContent() {
        String content = "<img src=\"test.jpg\" />";

        assertThat(NewsTextUtils.stripCoverImageFromContent(content, null)).isEqualTo(content);
    }

    @Test
    void stripCoverImageRemovesLeadingEmptyBlocks() {
        String cover = "https://example.com/article-header.jpg";
        String content = "<img src=\"https://example.com/article-header.jpg\" /><p></p><p>Real content</p>";

        String result = NewsTextUtils.stripCoverImageFromContent(content, cover);

        assertThat(result).startsWith("<p>Real content</p>");
    }

    @Test
    void stripCoverImageKeepsNonMatchingImages() {
        String cover = "https://example.com/cover.jpg";
        String content = "<img src=\"https://other.com/different.jpg\" /><p>Text</p>";

        String result = NewsTextUtils.stripCoverImageFromContent(content, cover);

        assertThat(result).contains("<img");
    }

    @Test
    void decodeHtmlUnescapesEntities() {
        assertThat(NewsTextUtils.decodeHtml("&amp; &lt; &gt;")).isEqualTo("& < >");
    }

    @Test
    void decodeHtmlNullReturnsNull() {
        assertThat(NewsTextUtils.decodeHtml(null)).isNull();
    }

    @Test
    void decodeHtmlEmptyReturnsEmpty() {
        assertThat(NewsTextUtils.decodeHtml("")).isEmpty();
    }
}
