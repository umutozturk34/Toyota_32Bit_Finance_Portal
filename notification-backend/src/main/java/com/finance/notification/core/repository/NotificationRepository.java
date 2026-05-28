package com.finance.notification.core.repository;

import com.finance.notification.core.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Persistence for {@link Notification}, with paged listing/search (all or unread-only), unread
 * counts, bulk mark-all-read, per-user purge and expired-row cleanup.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserSubOrderByCreatedAtDesc(String userSub, Pageable pageable);

    Page<Notification> findByUserSubAndReadAtIsNullOrderByCreatedAtDesc(String userSub, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.userSub = :userSub " +
            "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(n.body) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY n.createdAt DESC")
    Page<Notification> searchByUserSub(@Param("userSub") String userSub,
                                        @Param("search") String search,
                                        Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.userSub = :userSub AND n.readAt IS NULL " +
            "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(n.body) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY n.createdAt DESC")
    Page<Notification> searchUnreadByUserSub(@Param("userSub") String userSub,
                                              @Param("search") String search,
                                              Pageable pageable);

    long countByUserSubAndReadAtIsNull(String userSub);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.userSub = :userSub AND n.readAt IS NULL")
    int markAllRead(@Param("userSub") String userSub, @Param("readAt") LocalDateTime readAt);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.expiresAt IS NOT NULL AND n.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userSub = :userSub")
    int deleteAllByUserSub(@Param("userSub") String userSub);
}
