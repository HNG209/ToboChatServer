package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.annotations.RequirePermission;
import com.teamtobo.tobochatserver.annotations.RoomId;
import com.teamtobo.tobochatserver.dtos.request.AddMemberRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.GroupPendingRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.MemberPermission;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.services.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Room Controller")
@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomMemberService roomMemberService;
    private final RoomDomainService roomDomainService;
    private final GroupPendingRequestService groupPendingRequestService;

    @Operation(summary = "Tạo nhóm chat")
    @PostMapping
    public ResponseEntity<Void> createGroup(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RoomCreateRequest request) {
        String userId = jwt.getSubject();

        roomDomainService.createRoom(userId, request, RoomType.GROUP);
        return ResponseEntity.noContent().build();
    }

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

    @Operation(summary = "Thêm thành viên vào nhóm chat")
    @PostMapping("/{roomId}/members")
    @RequirePermission(MemberPermission.ADD_MEMBER)
    public ResponseEntity<Void> addMembers(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @RequestBody AddMemberRequest request) {

        String inviterId = jwt.getSubject();
        roomDomainService.addMemberToGroup(roomId, inviterId, request.getTargetUserIds());

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lấy danh sách pending request của nhóm")
    @GetMapping("/{roomId}/pending")
    public ApiResponse<PageResponse<GroupPendingRequestResponse>> getPending(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        String userId = jwt.getSubject();

        return ApiResponse.<PageResponse<GroupPendingRequestResponse>>builder()
                .result(groupPendingRequestService.getPending(roomId, userId, limit))
                .build();
    }

    @Operation(summary = "Phê duyệt hoặc từ chối thành viên vào group")
    @PostMapping("/{roomId}/approve/{userId}")
    @RequirePermission(MemberPermission.APPROVE_MEMBER)
    public ResponseEntity<Void> approve(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @PathVariable String userId,
            @Parameter(
                    description = "Chấp nhận hay từ chối",
                    required = true,
                    schema = @Schema(allowableValues = {"true", "false"})
            )
            @RequestParam boolean accept
    ) {
        String adminId = jwt.getSubject();
        roomDomainService.approveMember(roomId, adminId, userId, accept);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bật/tắt phê duyệt thành viên vào nhóm")
    @PatchMapping("/{roomId}/approval/toggle")
    @RequirePermission(MemberPermission.TOGGLE_APPROVE_MEMBER)
    public ResponseEntity<Void> toggleApproveMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String roomId
    ) {
        roomDomainService.toggleApproveMember(roomId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bật/tắt cho phép thêm thành viên khác vào nhóm")
    @PatchMapping("/{roomId}/add-member/toggle")
    @RequirePermission(MemberPermission.TOGGLE_ALLOW_ADD_MEMBER)
    public ResponseEntity<Void> toggleAllowAddMember(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId
    ) {
        roomDomainService.toggleAllowAddMember(roomId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bật/tắt cho phép thành viên gửi tin nhắn")
    @PatchMapping("/{roomId}/message/toggle")
    @RequirePermission(MemberPermission.TOGGLE_ALLOW_SEND_MESSAGE)
    public ResponseEntity<Void> toggleAllowSendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId
    ) {
        roomDomainService.toggleAllowSendMessage(roomId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bật/tắt cho phép thành viên cập nhật tên/avatar nhóm")
    @PatchMapping("/{roomId}/update-group/toggle")
    @RequirePermission(MemberPermission.TOGGLE_ALLOW_UPDATE_GROUP)
    public ResponseEntity<Void> toggleAllowUpdateGroup(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId
    ) {
        roomDomainService.toggleAllowUpdateGroup(roomId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Phân bố phó nhóm")
    @PatchMapping("/{roomId}/assign-vice-admin/{userId}")
    @RequirePermission(MemberPermission.ADD_VICE_ADMIN)
    public ResponseEntity<Void> addViceAdmin (
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @PathVariable String userId
    ) {
        roomDomainService.addViceAdmin(roomId, jwt.getSubject(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Xoá thành viên ra khỏi nhóm")
    @DeleteMapping("/{roomId}/remove-member/{userId}")
    @RequirePermission(MemberPermission.REMOVE_MEMBER)
    public ResponseEntity<Void> removeMember (
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @PathVariable String userId
    ) {
        roomDomainService.removeMember(roomId, jwt.getSubject(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Rời khỏi nhóm")
    @DeleteMapping("/{roomId}/leave-group")
    @RequirePermission(MemberPermission.LEAVE_GROUP)
    public ResponseEntity<Void> leaveGroup (
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId
    ) {
        roomDomainService.leaveGroup(roomId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
