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
import org.springframework.web.bind.annotation.*;

@Tag(name = "Test Contact Controller")
@RestController
@RequestMapping("/contacts/test")
@RequiredArgsConstructor
public class TestController {
    private final ContactService contactService;

    @Operation(summary = "Danh sách bạn bè của người dùng")
    @GetMapping("/friends")
    public ApiResponse<PageResponse<FriendResponse>> getFriends(
            @RequestParam String userId,
            @RequestParam(required = false) String roomId,
            @RequestParam(defaultValue = "0", required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {

        PageResponse<FriendResponse> friends = contactService.getFriends(userId, roomId, cursor, limit);
        return ApiResponse.<PageResponse<FriendResponse>>builder()
                .result(friends)
                .build();
    }

    @Operation(summary = "Lấy danh sách lời mời kết bạn")
    @GetMapping("/friend-requests")
    public ApiResponse<PageResponse<FriendRequestResponse>> getFriendRequests(
            @RequestParam String userId,
            @RequestParam(defaultValue = "SENT") FriendRequestType type,
            @RequestParam(defaultValue = "0", required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {

        PageResponse<FriendRequestResponse> friendRequests = contactService.getFriendRequests(type, userId, cursor, limit);
        return ApiResponse.<PageResponse<FriendRequestResponse>>builder()
                .result(friendRequests)
                .build();
    }

    @Operation(summary = "Gửi lời mời kết bạn")
    @PostMapping("/{otherId}")
    public ResponseEntity<Void> sendFriendRequest(
            @RequestParam String userId,
            @PathVariable String otherId) {

        contactService.sendFriendRequest(userId, otherId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Huỷ lời mời")
    @DeleteMapping("/{otherId}")
    public ResponseEntity<Void> cancelFriendRequest(
            @RequestParam String userId,
            @PathVariable String otherId) {

        contactService.cancelFriendRequest(userId, otherId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Trạng thái bạn bè")
    @GetMapping("/{otherId}/friend-status")
    public ApiResponse<FriendStatus> getFriendStatus(
            @RequestParam String userId,
            @PathVariable String otherId) {

        return ApiResponse.<FriendStatus>builder()
                .result(contactService.getFriendStatus(userId, otherId))
                .build();
    }
}
