package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.request.CreateDmRoomRequest;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.services.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Room", description = "APIs quản lý phòng chat")
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "Tạo hoặc lấy phòng DM với một người dùng khác",
               description = "Nếu phòng DM đã tồn tại thì trả về phòng đó. Kết quả trả về kèm thông tin bạn bè (peer).")
    @PostMapping("/dm")
    public ApiResponse<RoomResponse> getOrCreateDmRoom(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateDmRoomRequest request) {

        String userId = jwt.getSubject();
        RoomResponse room = roomService.getOrCreateDmRoom(userId, request);
        return ApiResponse.<RoomResponse>builder()
                .result(room)
                .build();
    }

    @Operation(summary = "Danh sách phòng chat của người dùng hiện tại",
               description = "Trả về tất cả các phòng. Phòng DM kèm theo thông tin bạn bè (peer).")
    @GetMapping
    public ApiResponse<List<RoomResponse>> getRooms(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<RoomResponse> rooms = roomService.getRooms(userId);
        return ApiResponse.<List<RoomResponse>>builder()
                .result(rooms)
                .build();
    }
}
