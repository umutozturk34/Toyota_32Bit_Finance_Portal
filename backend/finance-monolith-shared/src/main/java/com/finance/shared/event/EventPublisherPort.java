package com.finance.shared.event;

import com.finance.common.event.DomainEvent;

/**
 * Outbound seam for emitting {@link DomainEvent}s, decoupling producing modules from the messaging
 * transport. Implemented by the application-layer Kafka adapter.
 */
public interface EventPublisherPort {

    /** Publishes a domain event to the underlying message broker. */
    void publish(DomainEvent event);
}
