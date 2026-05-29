package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.annotations.RequireRoomMember;
import com.teamtobo.tobochatserver.annotations.RoomId;
import com.teamtobo.tobochatserver.dtos.request.*;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.enums.ReactionType;
import com.teamtobo.tobochatserver.services.ChatDomainService;
import com.teamtobo.tobochatserver.services.ChatService;
import com.teamtobo.tobochatserver.services.PollService;
import io.swagger.v3.oas.annotations.Operation;
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
    private final ChatDomainService chatDomainService;
    private final PollService pollService;

    @Operation(summary = "Danh sách tin nhắn của phòng hiện tại")
    @GetMapping("/rooms/{roomId}/messages")
    @RequireRoomMember
    public ApiResponse<PageResponse<MessageResponse>> getMessages(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "before") String direction
    ) {
        String userId = jwt.getSubject();

        return ApiResponse.<PageResponse<MessageResponse>>builder()
                .result(chatService.getMessages(userId, roomId, cursor, limit, direction))
                .build();
    }

    @Operation(summary = "Lấy 1 tin nhắn cụ thể")
    @GetMapping("/rooms/{roomId}/messages/{messageId}")
    @RequireRoomMember
    public ApiResponse<MessageResponse> getMessage(
            @RoomId @PathVariable String roomId,
            @PathVariable String messageId) {

        return ApiResponse.<MessageResponse>builder()
                .result(chatService.getMessage(messageId, roomId))
                .build();
    }

    @Operation(summary = "Gửi message")
    @PostMapping("/rooms/{roomId}/messages")
    @RequireRoomMember
    public ApiResponse<MessageResponse> sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @RequestBody SendMessageRequest request) {
        String senderId = jwt.getSubject();

        return ApiResponse.<MessageResponse>builder()
                .result(chatDomainService.sendMessage(senderId, roomId, request))
                .build();
    }

    @Operation(summary = "Thả reaction cho tin nhắn")
    @PostMapping("/rooms/{roomId}/messages/{messageId}/reactions")
    @RequireRoomMember
    public ResponseEntity<Void> addReaction(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @PathVariable String messageId,
            @RequestParam(defaultValue = "LIKE") ReactionType reactionType
            ) {
        String userId = jwt.getSubject();

        chatService.addReaction(userId, roomId, messageId, reactionType);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Danh sách reaction của 1 tin nhắn")
    @GetMapping("/rooms/{roomId}/messages/{messageId}/reactions")
    @RequireRoomMember
    public ApiResponse<PageResponse<MessageReactionResponse>> getMessageReactions(
            @RoomId @PathVariable String roomId,
            @PathVariable String messageId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.<PageResponse<MessageReactionResponse>>builder()
                .result(chatService.getMessageReactions(messageId, roomId, cursor, limit))
                .build();
    }

    @Operation(summary = "Thu hồi tin nhắn trong phòng")
    @PostMapping("/rooms/{roomId}/messages/revoke")
    @RequireRoomMember
    public ResponseEntity<?> revokeMessage(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @RequestBody RevokeMessageRequest request
    ) {
        String userId = jwt.getSubject();
        String messageId = request.getMessageId(); // chính là sk

        chatService.revokeMessage(userId, roomId, messageId);

        return ResponseEntity.ok().body("Thu hồi thành công");
    }

    @Operation(summary = "Gửi tin nhắn cho nhiều group")
    @PostMapping("/rooms/forwardMessage")
    public ResponseEntity<?> forwardMessage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ForwardRequest request) {
        String userId = jwt.getSubject();

        if (request.getFromRoomId() == null ||
                request.getToRoomIds() == null ||
                request.getMessageIds() == null) {
            return ResponseEntity.badRequest().body("Thiếu dữ liệu");
        }

        chatService.forwardMessages(
                userId,
                request.getFromRoomId(),
                request.getMessageIds(),
                request.getToRoomIds()
        );

        return ResponseEntity.ok("Forward thành công");

    }

    @GetMapping("/upload/{roomId}")
    public ApiResponse<PresignedUrlResponse> getAttachmentPresignedUrl(
            @PathVariable String roomId,
            @RequestParam String fileName,
            @RequestParam String contentType
    ) {
        return ApiResponse.<PresignedUrlResponse>builder()
                .result(chatService.generateAttachmentPresignedUrl(fileName, roomId, contentType)).build();
    }


    @Operation(summary = "Xoá tin nhắn ở phía tôi")
    @DeleteMapping("/rooms/{roomId}/messages/{messageId}")
    @RequireRoomMember
    public ResponseEntity<Void> deleteMessage(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @PathVariable String messageId
    ) {
        String userId = jwt.getSubject();

        chatService.deleteMessage(messageId, roomId, userId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Tạo poll")
    @PostMapping("/rooms/{roomId}/polls")
    public ResponseEntity<Void> createPoll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @RequestBody PollSubmitRequest request) throws Exception {
        String userId = jwt.getSubject();

        pollService.createPoll(userId, roomId, request);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Chỉnh sửa poll")
    @PutMapping("/rooms/{roomId}/polls/{pollId}")
    public ResponseEntity<Void> updatePoll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @RequestBody PollSubmitRequest request,
            @PathVariable String pollId) throws Exception {
        String userId = jwt.getSubject();

        pollService.updatePoll(roomId, pollId, request, userId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Vote poll")
    @PatchMapping("/rooms/{roomId}/polls/{pollId}")
    public ResponseEntity<Void> votePoll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @PathVariable String pollId,
            @RequestBody VotePollRequest request) throws Exception {
        String userId = jwt.getSubject();

        pollService.votePoll(roomId, pollId, request.getOptionIds(), userId);

        return ResponseEntity.noContent().build();
    }
}
