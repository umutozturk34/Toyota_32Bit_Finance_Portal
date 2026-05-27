package com.finance.common.event;

public interface DomainEvent {

    String eventId();

    String partitionKey();

    EventTopic topic();
}
