package com.finance.app.dto.response.overview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WidgetSectionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_substituteEmptyObject_when_configIsNull() {
        WidgetSection section = new WidgetSection("section-1", WidgetKind.MOVERS, 0, null);

        JsonNode config = section.config();

        assertThat(config).isNotNull();
        assertThat(config.isObject()).isTrue();
        assertThat(config.size()).isZero();
    }

    @Test
    void should_keepProvidedConfig_when_configIsNotNull() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"market\":\"STOCK\"}");

        WidgetSection section = new WidgetSection("section-2", WidgetKind.MOVERS, 1, payload);

        assertThat(section.config().get("market").asString()).isEqualTo("STOCK");
    }

    @Test
    void should_exposeAllFields_when_constructedWithFullPayload() {
        WidgetSection section = new WidgetSection("news-1", WidgetKind.NEWS, 5, null);

        assertThat(section.sectionId()).isEqualTo("news-1");
        assertThat(section.kind()).isEqualTo(WidgetKind.NEWS);
        assertThat(section.order()).isEqualTo(5);
    }
}
