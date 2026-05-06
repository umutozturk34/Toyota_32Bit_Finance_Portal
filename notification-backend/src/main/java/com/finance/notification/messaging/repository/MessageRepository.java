package com.finance.notification.messaging.repository;

import com.finance.notification.messaging.model.Message;
import com.finance.notification.messaging.model.MessageDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByRecipientSubOrderBySentAtDesc(String recipientSub, Pageable pageable);

    Page<Message> findByDirectionOrderBySentAtDesc(MessageDirection direction, Pageable pageable);

    Page<Message> findBySenderSubOrderBySentAtDesc(String senderSub, Pageable pageable);

    long countByRecipientSubAndReadAtIsNull(String recipientSub);

    long countByDirection(MessageDirection direction);

    @Query("SELECT m FROM Message m WHERE " +
            "(m.senderSub = :userSub AND m.direction = com.finance.notification.messaging.model.MessageDirection.USER_TO_ADMIN) OR " +
            "(m.recipientSub = :userSub AND m.direction = com.finance.notification.messaging.model.MessageDirection.ADMIN_TO_USER) " +
            "ORDER BY m.sentAt ASC")
    List<Message> findConversation(@Param("userSub") String userSub);

    @Modifying
    @Query("DELETE FROM Message m WHERE " +
            "(m.senderSub = :userSub AND m.direction = com.finance.notification.messaging.model.MessageDirection.USER_TO_ADMIN) OR " +
            "(m.recipientSub = :userSub AND m.direction = com.finance.notification.messaging.model.MessageDirection.ADMIN_TO_USER)")
    int deleteConversation(@Param("userSub") String userSub);

    @Query(value = """
            SELECT
                latest.user_sub                                AS userSub,
                latest.body                                    AS lastBody,
                latest.sent_at                                 AS lastSentAt,
                EXISTS (
                    SELECT 1 FROM closed_conversations c
                    WHERE c.user_sub = latest.user_sub
                )                                              AS closed,
                COALESCE((
                    SELECT COUNT(*) FROM messages u
                    WHERE u.direction = 'USER_TO_ADMIN'
                      AND u.sender_sub = latest.user_sub
                      AND u.read_at IS NULL
                ), 0)                                          AS unreadCount
            FROM (
                SELECT DISTINCT ON (user_sub)
                    CASE WHEN direction = 'USER_TO_ADMIN'
                         THEN sender_sub ELSE recipient_sub END AS user_sub,
                    body,
                    sent_at
                FROM messages
                ORDER BY user_sub, sent_at DESC
            ) latest
            WHERE (CAST(:bodyTerm AS text) IS NULL OR latest.body ILIKE CONCAT('%', CAST(:bodyTerm AS text), '%')
                   OR (:hasSubFilter = TRUE AND latest.user_sub = ANY(CAST(:subFilter AS text[]))))
            ORDER BY latest.sent_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM (
                SELECT DISTINCT
                    CASE WHEN direction = 'USER_TO_ADMIN'
                         THEN sender_sub ELSE recipient_sub END AS user_sub
                FROM messages m_inner
                WHERE (CAST(:bodyTerm AS text) IS NULL
                       OR m_inner.body ILIKE CONCAT('%', CAST(:bodyTerm AS text), '%')
                       OR (:hasSubFilter = TRUE
                           AND (CASE WHEN direction = 'USER_TO_ADMIN' THEN sender_sub ELSE recipient_sub END)
                               = ANY(CAST(:subFilter AS text[]))))
            ) distinct_users
            """,
            nativeQuery = true)
    Page<ConversationSummaryProjection> findConversationSummaries(
            @Param("bodyTerm") String bodyTerm,
            @Param("subFilter") String[] subFilter,
            @Param("hasSubFilter") boolean hasSubFilter,
            Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.readAt = :readAt " +
            "WHERE m.senderSub = :userSub " +
            "AND m.direction = com.finance.notification.messaging.model.MessageDirection.USER_TO_ADMIN " +
            "AND m.readAt IS NULL")
    int markAdminInboxRead(@Param("userSub") String userSub, @Param("readAt") java.time.LocalDateTime readAt);

    interface ConversationSummaryProjection {
        String getUserSub();
        String getLastBody();
        java.time.LocalDateTime getLastSentAt();
        Boolean getClosed();
        Long getUnreadCount();
    }
}
