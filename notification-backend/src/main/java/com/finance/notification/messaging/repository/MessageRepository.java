package com.finance.notification.messaging.repository;

import com.finance.notification.messaging.model.Message;
import com.finance.notification.messaging.model.MessageDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByRecipientSubOrderBySentAtDesc(String recipientSub, Pageable pageable);

    Page<Message> findByDirectionOrderBySentAtDesc(MessageDirection direction, Pageable pageable);

    Page<Message> findBySenderSubOrderBySentAtDesc(String senderSub, Pageable pageable);

    long countByRecipientSubAndReadAtIsNull(String recipientSub);

    long countByDirection(MessageDirection direction);
}
