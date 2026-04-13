package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.services.ChatRoomMemberService;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Room Controller")
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final ChatRoomMemberService chatRoomMemberService;
    private final RoomMemberService roomMemberService;
    private final RoomService roomService;

    @Operation(summary = "Danh sách phòng đã tham gia")
    @GetMapping
    public ApiResponse<PageResponse<RoomResponse>> getJoinedRooms(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "ACTIVE") InboxStatus status,
            @RequestParam(defaultValue = "10") int limit) {
        String userId = jwt.getSubject();

        PageResponse<RoomResponse> rooms = roomMemberService.getJoinedRooms(userId, cursor, limit, status);
        return ApiResponse.<PageResponse<RoomResponse>>builder()
                .result(rooms)
                .build();
    }

    @Operation(summary = "Lấy thông tin phòng")
    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> getRoomMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId) {
        String userId = jwt.getSubject();

        RoomResponse response = roomMemberService.getRoomMetadata(userId, roomId);
        return ApiResponse.<RoomResponse>builder()
                .result(response)
                .build();
    }

    @Operation(summary = "Đánh dấu một phòng là đã đọc")
    @PatchMapping("/{roomId}/read")
    public ApiResponse<Void> markAsRead (
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId) {
        String userId = jwt.getSubject();
        roomMemberService.markAsReadedMessage(userId, roomId);
        return ApiResponse.<Void>builder()
                .message("Đã đánh dấu phòng là đã đọc")
                .build();
    }
}
