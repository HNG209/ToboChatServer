package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.services.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Room", description = "APIs quản lý phòng chat")
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "Tạo hoặc lấy phòng DM với bạn bè. Trả về thông tin phòng kèm thông tin bạn bè.")
    @PostMapping("/dm/{friendId}")
    public ApiResponse<RoomResponse> createOrGetDmRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String friendId) {

        String userId = jwt.getSubject();
        RoomResponse room = roomService.createOrGetDmRoom(userId, friendId);
        return ApiResponse.<RoomResponse>builder()
                .result(room)
                .build();
    }

    @Operation(summary = "Lấy danh sách phòng chat của người dùng hiện tại. Với DM, name và avatarUrl là thông tin của người bạn.")
    @GetMapping
    public ApiResponse<PageResponse<RoomResponse>> getUserRooms(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {

        String userId = jwt.getSubject();
        PageResponse<RoomResponse> rooms = roomService.getUserRooms(userId, cursor, limit);
        return ApiResponse.<PageResponse<RoomResponse>>builder()
                .result(rooms)
                .build();
    }
}
