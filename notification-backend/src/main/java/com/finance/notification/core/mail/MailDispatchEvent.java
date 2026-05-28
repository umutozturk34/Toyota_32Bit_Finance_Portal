package com.finance.notification.core.mail;

/** Kafka event pointing at an outbox row to be sent; carries only the id so workers re-read state. */
public record MailDispatchEvent(Long outboxId) {
}
