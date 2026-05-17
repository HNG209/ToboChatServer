package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.FriendResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.services.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Contact Controller", description = "APIs quản lý contact")
@RestController
@RequestMapping("/contacts")
@RequiredArgsConstructor
public class ContactController {
    private final ContactService contactService;

    @Operation(summary = "Tìm người dùng bằng email")
    @GetMapping("/friends")
    public ApiResponse<PageResponse<FriendResponse>> getFriends(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String roomId,
            @RequestParam(defaultValue = "0", required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        String userId = jwt.getSubject();

        PageResponse<FriendResponse> friends = contactService.getFriends(userId, roomId, cursor, limit);
        return ApiResponse.<PageResponse<FriendResponse>>builder()
                .result(friends)
                .build();
    }
}
