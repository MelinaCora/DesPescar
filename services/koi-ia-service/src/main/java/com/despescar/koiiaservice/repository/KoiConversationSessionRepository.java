package com.despescar.koiiaservice.repository;

import com.despescar.koiiaservice.entity.KoiConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface KoiConversationSessionRepository extends JpaRepository<KoiConversationSession, UUID> {
    Optional<KoiConversationSession> findTopByUserIdentifierOrderByUpdatedAtDesc(String userIdentifier);
    List<KoiConversationSession> findByUserIdentifierOrderByUpdatedAtDesc(String userIdentifier);
}
