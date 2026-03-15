package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.request.CreateRoomRequest;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.services.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Room", description = "APIs quản lý phòng chat")
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;

    @Operation(summary = "Tạo phòng chat mới (DM hoặc GROUP)")
    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateRoomRequest request) {
        String userId = jwt.getSubject();

        RoomResponse room = roomService.createRoom(userId, request);
        return ResponseEntity.ok(ApiResponse.<RoomResponse>builder().result(room).build());
    }

    @Operation(summary = "Danh sách phòng chat của người dùng hiện tại")
    @GetMapping
    public ApiResponse<PageResponse<RoomResponse>> getRooms(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        String userId = jwt.getSubject();

        return ApiResponse.<PageResponse<RoomResponse>>builder()
                .result(roomService.getRooms(userId, cursor, limit))
                .build();
    }

    @Operation(summary = "Lấy thông tin phòng chat theo ID")
    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> getRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId) {
        String userId = jwt.getSubject();

        return ApiResponse.<RoomResponse>builder()
                .result(roomService.getRoom(userId, roomId))
                .build();
    }
}
