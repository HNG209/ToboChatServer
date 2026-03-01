package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.FriendEntity;
import com.teamtobo.tobochatserver.entities.enums.FriendRequestType;
import com.teamtobo.tobochatserver.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Friend Request", description = "APIs quản lý lời mời kết bạn")
@RestController
@RequestMapping("/friend-requests")
@RequiredArgsConstructor
public class FriendRequestController {
    private final UserService userService;

    @Operation(summary = "Gửi lời mời kết bạn")
    @PostMapping("/{otherId}")
    public ResponseEntity<Void> sendFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String otherId) {
        String userId = jwt.getSubject(); // sender

        userService.sendFriendRequest(userId, otherId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Huỷ lời mời")
    @DeleteMapping("/{otherId}")
    public ResponseEntity<Void> cancelFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String otherId) {
        String userId = jwt.getSubject();

        userService.cancelFriendRequest(userId, otherId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Phản hồi lời mời")
    @PutMapping("/{otherId}")
    public ResponseEntity<Void> responseFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "true") boolean accepted,
            @PathVariable String otherId) {
        String userId = jwt.getSubject(); // receiver

        userService.responseFriendRequest(userId, FriendAcceptRequest.builder()
                .accepted(accepted)
                .fromUser(otherId)
                .build());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lấy danh sách lời mời (đã gửi/đang chờ)")
    @GetMapping
    public ApiResponse<PageResponse<FriendEntity>> getFriendRequestList(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam FriendRequestType type,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {

        return ApiResponse.<PageResponse<FriendEntity>>builder()
                .result(userService.getFriendRequests(
                        type,
                        jwt.getSubject(),
                        cursor,
                        limit
                ))
                .build();
    }
}
