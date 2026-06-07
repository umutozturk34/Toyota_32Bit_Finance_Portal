package com.finance.news.mapper;

import com.finance.news.dto.request.UpsertNewsSourceRequest;
import com.finance.news.dto.response.NewsSourceResponse;
import com.finance.news.model.NewsSource;
import com.finance.news.model.NewsSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSourceMapperTest {

    private NewsSourceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new NewsSourceMapperImpl();
    }

    private UpsertNewsSourceRequest request(String name, String url) {
        UpsertNewsSourceRequest request = new UpsertNewsSourceRequest();
        request.setName(name);
        request.setUrl(url);
        return request;
    }

    @Test
    void should_exposeSourceTypeName_when_toResponse() {
        // Arrange
        NewsSource entity = NewsSource.builder()
                .id(3L)
                .name("CoinDesk")
                .url("https://coindesk.com/rss")
                .sourceType(NewsSourceType.RSS)
                .enabled(true)
                .sortOrder(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Act
        NewsSourceResponse response = mapper.toResponse(entity);

        // Assert
        assertThat(response.sourceType()).isEqualTo("RSS");
        assertThat(response.name()).isEqualTo("CoinDesk");
        assertThat(response.sortOrder()).isEqualTo(5);
    }

    @Test
    void should_returnNull_when_toResponseGivenNull() {
        // Arrange + Act
        NewsSourceResponse response = mapper.toResponse(null);

        // Assert
        assertThat(response).isNull();
    }

    @Test
    void should_mapEachElement_when_toResponses() {
        // Arrange
        NewsSource entity = NewsSource.builder().id(1L).name("A").url("u")
                .sourceType(NewsSourceType.RSS).build();

        // Act
        List<NewsSourceResponse> responses = mapper.toResponses(List.of(entity));

        // Assert
        assertThat(responses).singleElement()
                .satisfies(r -> assertThat(r.name()).isEqualTo("A"));
    }

    @Test
    void should_trimNameAndUrl_when_toEntity() {
        // Arrange
        UpsertNewsSourceRequest request = request("  CoinDesk  ", "  https://coindesk.com/rss  ");

        // Act
        NewsSource entity = mapper.toEntity(request);

        // Assert
        assertThat(entity.getName()).isEqualTo("CoinDesk");
        assertThat(entity.getUrl()).isEqualTo("https://coindesk.com/rss");
    }

    @Test
    void should_applyEnabledAndSortDefaults_when_toEntityGivenNullFlags() {
        // Arrange
        UpsertNewsSourceRequest request = request("Name", "url");
        request.setEnabled(null);
        request.setSortOrder(null);

        // Act
        NewsSource entity = mapper.toEntity(request);

        // Assert
        assertThat(entity.isEnabled()).isTrue();
        assertThat(entity.getSortOrder()).isZero();
    }

    @Test
    void should_keepExplicitFlags_when_toEntityGivenValues() {
        // Arrange
        UpsertNewsSourceRequest request = request("Name", "url");
        request.setEnabled(false);
        request.setSortOrder(9);

        // Act
        NewsSource entity = mapper.toEntity(request);

        // Assert
        assertThat(entity.isEnabled()).isFalse();
        assertThat(entity.getSortOrder()).isEqualTo(9);
    }

    @Test
    void should_overwriteFieldsOnExistingEntity_when_updateEntity() {
        // Arrange
        NewsSource entity = NewsSource.builder().id(2L).name("old").url("oldurl").build();
        UpsertNewsSourceRequest request = request("  new name  ", "  newurl  ");
        request.setSortOrder(4);

        // Act
        mapper.updateEntity(request, entity);

        // Assert
        assertThat(entity.getId()).isEqualTo(2L);
        assertThat(entity.getName()).isEqualTo("new name");
        assertThat(entity.getUrl()).isEqualTo("newurl");
        assertThat(entity.getSortOrder()).isEqualTo(4);
    }

    @Test
    void should_parseSourceTypeCaseInsensitively_when_toEntity() {
        // Arrange
        UpsertNewsSourceRequest request = request("Name", "url");
        request.setSourceType("rss");

        // Act
        NewsSource entity = mapper.toEntity(request);

        // Assert
        assertThat(entity.getSourceType()).isEqualTo(NewsSourceType.RSS);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @CsvSource({ "'   '" })
    void should_defaultSourceTypeToRss_when_blankOrNull(String rawType) {
        // Arrange
        UpsertNewsSourceRequest request = request("Name", "url");
        request.setSourceType(rawType);

        // Act
        NewsSource entity = mapper.toEntity(request);

        // Assert
        assertThat(entity.getSourceType()).isEqualTo(NewsSourceType.RSS);
    }
}
