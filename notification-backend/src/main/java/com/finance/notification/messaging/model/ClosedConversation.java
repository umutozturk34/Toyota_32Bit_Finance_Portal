package com.finance.notification.messaging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "closed_conversations")
public class ClosedConversation {

    @Id
    @Column(name = "user_sub", length = 64)
    private String userSub;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    @Column(name = "closed_by_sub", nullable = false, length = 64)
    private String closedBySub;

    @PrePersist
    void prePersist() {
        if (closedAt == null) closedAt = LocalDateTime.now();
    }
}
