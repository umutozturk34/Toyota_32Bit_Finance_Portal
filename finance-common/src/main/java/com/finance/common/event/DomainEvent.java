package com.finance.common.event;

/**
 * Contract for all Kafka-published domain events. {@link #eventId()} uniquely identifies an
 * occurrence (for idempotency/tracing), {@link #partitionKey()} controls per-key ordering, and
 * {@link #topic()} names the logical destination resolved to a concrete topic via
 * {@link KafkaTopicsProperties}.
 */
public interface DomainEvent {

    String eventId();

    String partitionKey();

    EventTopic topic();
}
