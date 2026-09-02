package com.despescar.koiiaservice.controller;

import com.despescar.koiiaservice.dto.request.KoiConversationMessageRequest;
import com.despescar.koiiaservice.dto.response.KoiConversationResponse;
import com.despescar.koiiaservice.dto.response.KoiSessionResponse;
import com.despescar.koiiaservice.service.KoiConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/koi")
@RequiredArgsConstructor
public class KoiConversationController {

    private final KoiConversationService koiConversationService;

    @PostMapping("/sessions")
    public ResponseEntity<KoiConversationResponse> createSession() {
        return ResponseEntity.status(HttpStatus.CREATED).body(koiConversationService.startSession());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<KoiSessionResponse> getSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(koiConversationService.getSession(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<KoiConversationResponse> sendMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody KoiConversationMessageRequest request
    ) {
        return ResponseEntity.ok(koiConversationService.handleMessage(sessionId, request));
    }
}
