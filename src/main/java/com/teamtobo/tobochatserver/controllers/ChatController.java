package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
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

    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<PageResponse<MessageResponse>> getMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "before") String direction
    ) {
        String userId = jwt.getSubject();

        return ApiResponse.<PageResponse<MessageResponse>>builder()
                .result(chatService.getMessages(userId, roomId, cursor, limit, direction))
                .build();
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @RequestBody SendMessageRequest request) {
        String senderId = jwt.getSubject();

        chatService.sendMessage(senderId, roomId, request);

        return ResponseEntity.ok().body("Tin nhắn đã được lưu và đưa vào luồng gửi Socket");
    }

    @DeleteMapping("/rooms/{roomId}/messages/{messageId}")
    public ApiResponse<?> deleteMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @PathVariable String messageId
    ) {
        String userId = jwt.getSubject();

        chatService.deleteMessage(messageId, roomId, userId);

        return ApiResponse.builder()
                .message("Đã xoá tin nhắn phía bạn")
                .build();
    }
}
