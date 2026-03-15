package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.services.UserService;
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

    private final UserService userService;

    @Operation(summary = "Danh sách phòng chat của người dùng hiện tại (DM & Group)")
    @GetMapping
    public ApiResponse<PageResponse<RoomResponse>> getMyRooms(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        String userId = jwt.getSubject();
        return ApiResponse.<PageResponse<RoomResponse>>builder()
                .result(userService.getRooms(userId, cursor, limit))
                .build();
    }
}
