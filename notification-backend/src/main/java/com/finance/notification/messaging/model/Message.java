package com.finance.notification.messaging.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "messages",
        indexes = {
                @Index(name = "idx_messages_recipient_read", columnList = "recipient_sub, read_at"),
                @Index(name = "idx_messages_direction_sent", columnList = "direction, sent_at"),
                @Index(name = "idx_messages_sender_sent", columnList = "sender_sub, sent_at")
        })
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "sender_sub", nullable = false, length = 64)
    private String senderSub;

    @Column(name = "recipient_sub", length = 64)
    private String recipientSub;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private MessageDirection direction;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    void prePersist() {
        if (sentAt == null) sentAt = LocalDateTime.now();
    }
}
