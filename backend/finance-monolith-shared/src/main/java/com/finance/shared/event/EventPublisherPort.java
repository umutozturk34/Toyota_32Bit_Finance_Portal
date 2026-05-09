package com.finance.shared.event;

import com.finance.common.event.DomainEvent;

public interface EventPublisherPort {

    void publish(DomainEvent event);
}
