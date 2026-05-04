package com.finance.backend.repository;

import com.finance.backend.dto.enums.MessageDirection;
import com.finance.backend.model.Message;
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
