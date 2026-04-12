package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.request.ForwardRequest;
import com.teamtobo.tobochatserver.dtos.request.RevokeMessageRequest;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.services.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
            @RequestParam(defaultValue = "10") int limit
    ) {
        String userId = jwt.getSubject();

        return ApiResponse.<PageResponse<MessageResponse>>builder()
                .result(chatService.getMessages(userId, roomId, cursor, limit))
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


    @Operation(summary = "Xóa tin nhắn trong phòng")
    @PostMapping("/rooms/{roomId}/messages/revoke")
    public ResponseEntity<?> revokeMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @RequestBody RevokeMessageRequest request
    ) {
        String userId = jwt.getSubject();
        String messageId = request.getMessageId(); // chính là sk

        chatService.revokeMessage(userId, roomId, messageId);

        return ResponseEntity.ok().body("Thu hồi thành công");
    }
    @Operation(summary = "Gửi tin nhắn cho nhiều group ( có thể gưỉ nhìu tin nhắn một lần)")
    @PostMapping("/rooms/forwardMessage")
    public ResponseEntity<?> forwardMessage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ForwardRequest request) {
        String userId = jwt.getSubject();

        if (request.getFromRoomId() == null ||
                request.getToRoomIds() == null ||
                request.getMessageIds()== null) {
            return ResponseEntity.badRequest().body("Thiếu dữ liệu");
        }

        chatService.forwardToMultipleRooms(
                userId,
                request.getFromRoomId(),
                request.getToRoomIds(),
                request.getMessageIds()
        );

        return ResponseEntity.ok("Forward thành công");

    }


}
