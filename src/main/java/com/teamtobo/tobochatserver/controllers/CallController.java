package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.annotations.RequireRoomMember;
import com.teamtobo.tobochatserver.annotations.RoomId;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.services.handlers.CallSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
public class CallController {
    private final CallSessionManager callSessionManager;

    @GetMapping("/status/{roomId}")
    @RequireRoomMember
    public ApiResponse<Boolean> getCallStatus(
            @RoomId @PathVariable String roomId) {
        boolean isActive = callSessionManager.isCallActive(roomId);
        return ApiResponse.<Boolean>builder()
                .result(isActive)
                .build();
    }
}