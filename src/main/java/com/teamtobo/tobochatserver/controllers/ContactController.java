package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.FriendRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.FriendResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.enums.FriendRequestType;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;
import com.teamtobo.tobochatserver.services.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Contact Controller", description = "APIs quản lý contact")
@RestController
@RequestMapping("/contacts")
@RequiredArgsConstructor
public class ContactController {
    private final ContactService contactService;

    @Operation(summary = "Tìm người dùng bằng email")
    @GetMapping("/friends")
    public ApiResponse<PageResponse<FriendResponse>> getFriends(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String roomId,
            @RequestParam(defaultValue = "0", required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        String userId = jwt.getSubject();

        PageResponse<FriendResponse> friends = contactService.getFriends(userId, roomId, cursor, limit);
        return ApiResponse.<PageResponse<FriendResponse>>builder()
                .result(friends)
                .build();
    }

    @GetMapping("/friend-requests")
    public ApiResponse<PageResponse<FriendRequestResponse>> getFriendRequests(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "SENT") FriendRequestType type,
            @RequestParam(defaultValue = "0", required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        String userId = jwt.getSubject();

        PageResponse<FriendRequestResponse> friendRequests = contactService.getFriendRequests(type, userId, cursor, limit);
        return ApiResponse.<PageResponse<FriendRequestResponse>>builder()
                .result(friendRequests)
                .build();
    }

    @Operation(summary = "Gửi lời mời kết bạn")
    @PostMapping("/{otherId}")
    public ResponseEntity<Void> sendFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String otherId) {
        String userId = jwt.getSubject(); // sender

        contactService.sendFriendRequest(userId, otherId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Huỷ lời mời")
    @DeleteMapping("/{otherId}")
    public ResponseEntity<Void> cancelFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String otherId) {
        String userId = jwt.getSubject();

        contactService.cancelFriendRequest(userId, otherId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Trạng thái bạn bè")
    @GetMapping("/{otherId}/friend-status")
    public ApiResponse<FriendStatus> getFriendStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String otherId) {
        String userId = jwt.getSubject();

        return ApiResponse.<FriendStatus>builder()
                .result(contactService.getFriendStatus(userId, otherId))
                .build();
    }
}
