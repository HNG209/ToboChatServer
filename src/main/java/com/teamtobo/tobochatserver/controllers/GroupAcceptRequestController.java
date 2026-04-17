package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.GroupAcceptRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.services.GroupAcceptRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Group Invite", description = "APIs quản lý lời mời vào nhóm")
@RestController
@RequestMapping("/group-invites")
@RequiredArgsConstructor
public class GroupAcceptRequestController {

    private final GroupAcceptRequestService groupAcceptRequestService;

    @Operation(summary = "Lấy danh sách lời mời vào nhóm")
    @GetMapping
    public ApiResponse<PageResponse<GroupAcceptRequestResponse>> getInvites(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.<PageResponse<GroupAcceptRequestResponse>>builder()
                .result(groupAcceptRequestService.getInvites(jwt.getSubject(), cursor, limit))
                .build();
    }

    @Operation(summary = "Phản hồi lời mời vào nhóm")
    @PutMapping("/{roomId}")
    public ResponseEntity<Void> respondInvite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @RequestParam(defaultValue = "true") boolean accepted
    ) {
        groupAcceptRequestService.respondInvite(jwt.getSubject(), roomId, accepted);
        return ResponseEntity.noContent().build();
    }
}