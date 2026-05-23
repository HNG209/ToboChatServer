package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.annotations.RequireRoomMember;
import com.teamtobo.tobochatserver.annotations.RoomId;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.entities.enums.CallStatus;
import com.teamtobo.tobochatserver.services.CallService;
import com.teamtobo.tobochatserver.services.handlers.CallSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
public class CallController {
    private final CallService callService;

    @GetMapping("/status/{roomId}")
    @RequireRoomMember
    public ApiResponse<CallStatus> getCallStatus(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId) {
        String userId = jwt.getSubject();

        return ApiResponse.<CallStatus>builder()
                .result(callService.getCallStatus(userId, roomId))
                .build();
    }
}