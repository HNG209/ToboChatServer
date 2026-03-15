package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
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
    private final RoomService roomService;

    @Operation(summary = "Danh sách phòng đã tham gia")
    @GetMapping
    public ApiResponse<PageResponse<RoomResponse>> getJoinedRooms(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        String userId = jwt.getSubject();

        PageResponse<RoomResponse> rooms = roomService.getJoinedRooms(userId, cursor, limit);
        return ApiResponse.<PageResponse<RoomResponse>>builder()
                .result(rooms)
                .build();
    }
}
