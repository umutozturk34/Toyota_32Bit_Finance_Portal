package com.finance.common.event;

public interface DomainEvent {

    String eventId();

    String topic();

    String partitionKey();
}
