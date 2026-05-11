package com.finance.notification.core.mail;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"),
            @jakarta.persistence.QueryHint(name = "org.hibernate.lockOptions.followOn", value = "false")
    })
    @Query("""
            SELECT o FROM EmailOutbox o
            WHERE o.status = com.finance.notification.core.mail.EmailOutbox.Status.PENDING
              AND o.nextAttemptAt <= CURRENT_TIMESTAMP
            ORDER BY o.nextAttemptAt
            """)
    List<EmailOutbox> findPendingForProcessing(Pageable pageable);

    @Modifying
    @Query("""
            UPDATE EmailOutbox o
            SET o.status = com.finance.notification.core.mail.EmailOutbox.Status.PROCESSING
            WHERE o.id = :id
              AND o.status = com.finance.notification.core.mail.EmailOutbox.Status.RELAYED
            """)
    int claimForProcessing(@Param("id") Long id);
}
