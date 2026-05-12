package com.finance.app.service.overview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.service.UserLayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OverviewLayoutReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserLayoutService userLayoutService;
    private OverviewLayoutReader reader;

    @BeforeEach
    void setUp() {
        userLayoutService = mock(UserLayoutService.class);
        reader = new OverviewLayoutReader(userLayoutService, new OverviewDefaults(OverviewPropertiesFixture.standard()));
    }

    private UserLayoutResponse response(JsonNode overview) {
        return new UserLayoutResponse("user-1", overview, Instant.now());
    }

    @Test
    void should_returnDefaults_when_responseIsNull() {
        when(userLayoutService.getOrEmpty("user-1")).thenReturn(null);

        List<WidgetSection> sections = reader.readVisibleSections("user-1");

        assertThat(sections).isNotEmpty();
        assertThat(sections).extracting(WidgetSection::sectionId).contains("asset-cards-default", "movers-stock", "news-default");
    }

    @Test
    void should_returnDefaults_when_overviewIsEmpty() throws Exception {
        when(userLayoutService.getOrEmpty("user-1")).thenReturn(response(objectMapper.readTree("{}")));

        List<WidgetSection> sections = reader.readVisibleSections("user-1");

        assertThat(sections).isNotEmpty();
        assertThat(sections.size()).isEqualTo(7);
    }

    @Test
    void should_returnDefaults_when_sectionsArrayMissing() throws Exception {
        when(userLayoutService.getOrEmpty("user-1")).thenReturn(response(objectMapper.readTree("{\"foo\":\"bar\"}")));

        List<WidgetSection> sections = reader.readVisibleSections("user-1");

        assertThat(sections).isNotEmpty();
    }

    @Test
    void should_filterOutInvisibleSections_when_userToggledHidden() throws Exception {
        JsonNode overview = objectMapper.readTree("""
                {"sections":[
                  {"sectionId":"news","kind":"NEWS","visible":false,"order":0,"config":{}},
                  {"sectionId":"movers-stock","kind":"MOVERS","visible":true,"order":1,"config":{"market":"STOCK"}}
                ]}""");
        when(userLayoutService.getOrEmpty("user-1")).thenReturn(response(overview));

        List<WidgetSection> sections = reader.readVisibleSections("user-1");

        assertThat(sections).extracting(WidgetSection::sectionId).contains("movers-stock");
        assertThat(sections).extracting(WidgetSection::sectionId).doesNotContain("news");
    }

    @Test
    void should_translateLegacyBistIndicesId_when_v1ShapePresent() throws Exception {
        JsonNode overview = objectMapper.readTree("""
                {"sections":[
                  {"sectionId":"bist-indices","visible":true,"order":0,"config":{}}
                ]}""");
        when(userLayoutService.getOrEmpty("user-1")).thenReturn(response(overview));

        List<WidgetSection> sections = reader.readVisibleSections("user-1");

        assertThat(sections).extracting(WidgetSection::sectionId).contains("asset-cards-default");
        assertThat(sections.get(0).kind()).isEqualTo(WidgetKind.ASSET_CARDS);
    }

    @Test
    void should_dropEntries_when_kindIsUnknown() throws Exception {
        JsonNode overview = objectMapper.readTree("""
                {"sections":[
                  {"sectionId":"weird","kind":"GIBBERISH","visible":true,"order":0},
                  {"sectionId":"news","kind":"NEWS","visible":true,"order":1,"config":{}}
                ]}""");
        when(userLayoutService.getOrEmpty("user-1")).thenReturn(response(overview));

        List<WidgetSection> sections = reader.readVisibleSections("user-1");

        assertThat(sections).extracting(WidgetSection::sectionId).contains("news");
        assertThat(sections).extracting(WidgetSection::sectionId).doesNotContain("weird");
    }

    @Test
    void should_orderSectionsByOrderField_when_outOfOrderInJson() throws Exception {
        JsonNode overview = objectMapper.readTree("""
                {"sections":[
                  {"sectionId":"news","kind":"NEWS","visible":true,"order":5},
                  {"sectionId":"movers-stock","kind":"MOVERS","visible":true,"order":1,"config":{"market":"STOCK"}}
                ]}""");
        when(userLayoutService.getOrEmpty("user-1")).thenReturn(response(overview));

        List<WidgetSection> sections = reader.readVisibleSections("user-1");

        int newsIdx = -1, moversIdx = -1;
        for (int i = 0; i < sections.size(); i++) {
            if ("news".equals(sections.get(i).sectionId())) newsIdx = i;
            if ("movers-stock".equals(sections.get(i).sectionId())) moversIdx = i;
        }
        assertThat(moversIdx).isLessThan(newsIdx);
    }

    @Test
    void should_returnOnlyExplicitSections_when_userLayoutPartial() throws Exception {
        JsonNode overview = objectMapper.readTree("""
                {"sections":[
                  {"sectionId":"movers-stock","kind":"MOVERS","visible":true,"order":0,"config":{"market":"STOCK"}}
                ]}""");
        when(userLayoutService.getOrEmpty("user-1")).thenReturn(response(overview));

        List<WidgetSection> sections = reader.readVisibleSections("user-1");

        assertThat(sections).extracting(WidgetSection::sectionId).containsExactly("movers-stock");
    }
}
