package com.finance.common.event;

import com.finance.common.model.MarketType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventsTest {

    @Test
    void marketUpdatedEvent_of_setsRandomEventIdAndCurrentTimestamp() {
        MarketUpdatedEvent event = MarketUpdatedEvent.of(MarketType.STOCK, "scheduler");

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.marketType()).isEqualTo(MarketType.STOCK);
        assertThat(event.source()).isEqualTo("scheduler");
        assertThat(event.partitionKey()).isEqualTo("STOCK");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void newsPublishedEvent_of_setsRandomEventIdAndPartitionsByEventId() {
        NewsPublishedEvent event = NewsPublishedEvent.of("scheduler");

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.partitionKey()).isEqualTo(event.eventId());
        assertThat(event.source()).isEqualTo("scheduler");
    }

    @Test
    void portfolioUpdatedEvent_of_setsRandomEventIdAndPartitionsByEventId() {
        PortfolioUpdatedEvent event = PortfolioUpdatedEvent.of("snapshot-service");

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.partitionKey()).isEqualTo(event.eventId());
        assertThat(event.source()).isEqualTo("snapshot-service");
    }

    @Test
    void emailChangeCodeRequestedEvent_partitionKey_returnsUserSub() {
        EmailChangeCodeRequestedEvent event = new EmailChangeCodeRequestedEvent(
                "evt-1", "user-sub-1", "old@example.com", "new@example.com",
                "ABC123", "dark", OffsetDateTime.now().plusMinutes(15),
                OffsetDateTime.now());

        assertThat(event.partitionKey()).isEqualTo("user-sub-1");
        assertThat(event.code()).isEqualTo("ABC123");
        assertThat(event.theme()).isEqualTo("dark");
    }

    @Test
    void events_returnDistinctEventIds_acrossInstances() {
        MarketUpdatedEvent a = MarketUpdatedEvent.of(MarketType.CRYPTO, "a");
        MarketUpdatedEvent b = MarketUpdatedEvent.of(MarketType.CRYPTO, "b");

        assertThat(a.eventId()).isNotEqualTo(b.eventId());
    }

    @Test
    void macroIndicatorsUpdatedEvent_of_copiesChangedCodes() {
        MacroIndicatorsUpdatedEvent event = MacroIndicatorsUpdatedEvent.of(
                "scheduler", java.util.List.of("CPI", "PPI"));

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.source()).isEqualTo("scheduler");
        assertThat(event.changedCodes()).containsExactly("CPI", "PPI");
        assertThat(event.partitionKey()).isEqualTo(event.eventId());
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void macroIndicatorsUpdatedEvent_of_replacesNullCodesWithEmptyList() {
        MacroIndicatorsUpdatedEvent event = MacroIndicatorsUpdatedEvent.of("scheduler", null);

        assertThat(event.changedCodes()).isEmpty();
    }

    @Test
    void userRegisteredEvent_partitionsByUserSub_andTargetsUserRegisteredTopic() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                "evt-9", "user-sub-9", OffsetDateTime.now());

        assertThat(event.partitionKey()).isEqualTo("user-sub-9");
        assertThat(event.topic()).isEqualTo(EventTopic.USER_REGISTERED);
    }

    @Test
    void marketUpdatedEvent_topic_isMarketUpdated() {
        assertThat(MarketUpdatedEvent.of(MarketType.STOCK, "s").topic())
                .isEqualTo(EventTopic.MARKET_UPDATED);
    }

    @Test
    void newsPublishedEvent_topic_isNewsPublished() {
        assertThat(NewsPublishedEvent.of("s").topic()).isEqualTo(EventTopic.NEWS_PUBLISHED);
    }

    @Test
    void portfolioUpdatedEvent_topic_isPortfolioUpdated() {
        assertThat(PortfolioUpdatedEvent.of("s").topic()).isEqualTo(EventTopic.PORTFOLIO_UPDATED);
    }

    @Test
    void macroIndicatorsUpdatedEvent_topic_isMacroIndicatorsUpdated() {
        assertThat(MacroIndicatorsUpdatedEvent.of("s", java.util.List.of()).topic())
                .isEqualTo(EventTopic.MACRO_INDICATORS_UPDATED);
    }

    @Test
    void emailChangeCodeRequestedEvent_topic_isUserEmailChangeCode() {
        EmailChangeCodeRequestedEvent event = new EmailChangeCodeRequestedEvent(
                "evt-2", "user-sub-2", "old@example.com", "new@example.com",
                "XYZ789", "light", OffsetDateTime.now().plusMinutes(15), OffsetDateTime.now());

        assertThat(event.topic()).isEqualTo(EventTopic.USER_EMAIL_CHANGE_CODE);
    }
}
