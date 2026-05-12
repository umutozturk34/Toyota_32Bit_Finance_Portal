package com.finance.news.mapper;

import com.finance.news.config.NewsProperties;
import com.finance.news.dto.external.NewsArticleDto;
import com.finance.news.dto.internal.RssArticleData;
import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NewsArticleMapperTest {

    private NewsArticleMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new NewsArticleMapperImpl();
        Field field = NewsArticleMapper.class.getDeclaredField("newsProperties");
        field.setAccessible(true);
        field.set(mapper, new NewsProperties());
    }

    private RssArticleData data(String description, String content) {
        return new RssArticleData("Borsa İstanbul Türk Hava Yolları", "http://example.com",
                description, content, "http://img", "guid-1",
                LocalDateTime.of(2026, 5, 12, 0, 0));
    }

    @Test
    void toDto_returnsMapped_whenDescriptionPresentAndCategoryResolves() {
        RssArticleData rd = data("Türk Hava Yolları hisse fiyatı yükseldi piyasa açıldı", null);

        NewsArticleDto dto = mapper.toDto(rd, "BorsaAjans", "http://borsa.com", "BORSA_ISTANBUL");

        assertThat(dto).isNotNull();
        assertThat(dto.title()).contains("Türk Hava Yolları");
        assertThat(dto.sourceName()).isEqualTo("BorsaAjans");
    }

    @Test
    void toDto_returnsNull_whenBothDescriptionAndContentBlank() {
        RssArticleData rd = data(null, null);

        NewsArticleDto dto = mapper.toDto(rd, "BorsaAjans", "http://borsa.com", "BORSA_ISTANBUL");

        assertThat(dto).isNull();
    }

    @Test
    void toDto_concatenatesContentIntoResolverDescription_whenDescriptionShort() {
        RssArticleData rd = data("kısa", "<p>uzun içerik metni buraya geliyor</p>");

        NewsArticleDto dto = mapper.toDto(rd, "BorsaAjans", "http://borsa.com", "BORSA_ISTANBUL");

        assertThat(dto).isNotNull();
        assertThat(dto.description()).isEqualTo("kısa");
        assertThat(dto.content()).isEqualTo("<p>uzun içerik metni buraya geliyor</p>");
    }

    @Test
    void toDto_usesContentOnly_whenDescriptionMissingButContentPresent() {
        RssArticleData rd = data(null, "<p>content body for category</p>");

        NewsArticleDto dto = mapper.toDto(rd, "BorsaAjans", "http://borsa.com", "BORSA_ISTANBUL");

        assertThat(dto).isNotNull();
    }

    @Test
    void toEntity_mapsDtoFields_andAppliesFetchedAt() {
        NewsArticleDto dto = new NewsArticleDto("title", "http://example.com",
                "desc", "<p>content</p>", "BorsaAjans", "http://borsa.com",
                NewsCategory.BORSA_ISTANBUL, LocalDateTime.of(2026, 1, 1, 0, 0),
                "http://img", "guid-1");
        LocalDateTime now = LocalDateTime.of(2026, 5, 12, 0, 0);

        NewsArticle entity = mapper.toEntity(dto, now);

        assertThat(entity.getTitle()).isEqualTo("title");
        assertThat(entity.getCategory()).isEqualTo(NewsCategory.BORSA_ISTANBUL);
        assertThat(entity.getFetchedAt()).isEqualTo(now);
    }
}
