package com.despescar.koiiaservice.repository;

import com.despescar.koiiaservice.entity.KoiConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KoiConversationMessageRepository extends JpaRepository<KoiConversationMessage, UUID> {
    List<KoiConversationMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
