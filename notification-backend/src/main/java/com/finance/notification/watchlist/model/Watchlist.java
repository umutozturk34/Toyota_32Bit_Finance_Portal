package com.finance.notification.watchlist.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "watchlists",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_watchlists_user_name", columnNames = {"user_sub", "name"})
        },
        indexes = {
                @Index(name = "idx_watchlists_user_default_created",
                        columnList = "user_sub, is_default DESC, created_at ASC")
        })
public class Watchlist {

    public static final String DEFAULT_NAME = "Favoriler";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean belongsTo(String candidateUserSub) {
        return userSub.equals(candidateUserSub);
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public static Watchlist createDefault(String userSub) {
        return Watchlist.builder()
                .userSub(userSub)
                .name(DEFAULT_NAME)
                .isDefault(true)
                .build();
    }

    public static Watchlist create(String userSub, String name) {
        return Watchlist.builder()
                .userSub(userSub)
                .name(name)
                .isDefault(false)
                .build();
    }
}
