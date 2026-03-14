package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.services.ChatService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Chat Controller", description = "APIs quản lý chat")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @RequestBody SendMessageRequest request) {
        String senderId = jwt.getSubject();

        chatService.sendMessage(senderId, roomId, request);

        return ResponseEntity.ok().body("Tin nhắn đã được lưu và đưa vào luồng gửi Socket");
    }
}
