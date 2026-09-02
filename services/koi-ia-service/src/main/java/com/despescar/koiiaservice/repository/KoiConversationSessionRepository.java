package com.despescar.koiiaservice.repository;

import com.despescar.koiiaservice.entity.KoiConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KoiConversationSessionRepository extends JpaRepository<KoiConversationSession, UUID> {
}
