package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.annotations.*;
import com.teamtobo.tobochatserver.dtos.request.*;
import com.teamtobo.tobochatserver.dtos.response.*;
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
    public ApiResponse<RoomResponse> createGroup(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RoomCreateRequest request) {
        String userId = jwt.getSubject();

        return ApiResponse.<RoomResponse>builder()
                .result(roomDomainService.createRoom(userId, request, RoomType.GROUP))
                .build();
    }

    @Operation(summary = "Cài đặt trạng thái phòng")
    @PatchMapping("/{roomId}")
    @RequireAdmin
    public ResponseEntity<Void> updateRoomSettings(
            @RoomId @PathVariable String roomId,
            @RequestBody RoomUpdateRequest request
            ) {

        roomDomainService.updateRoomSettings(roomId, request);
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

    @Operation(summary = "Thông tin của tôi trong phòng")
    @GetMapping("/{roomId}/me")
    @RequireRoomMember
    public ApiResponse<RoomMemberResponse> getMyInfo(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId) {
        String userId = jwt.getSubject();

        return ApiResponse.<RoomMemberResponse>builder()
                .result(roomMemberService.getMember(userId, roomId))
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

    // TODO: Hàm của hệ thống, hệ thống làm tự động
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
    @RequireAddToGroupEnabled
    public ResponseEntity<Void> addMembers(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @RequestBody AddMemberRequest request) {

        String inviterId = jwt.getSubject();
        roomDomainService.addMemberToGroup(roomId, inviterId, request.getTargetUserIds());

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lấy danh sách pending request của nhóm")
    @GetMapping("/{roomId}/pending-requests")
    @RequirePermission(MemberPermission.GET_PENDING_REQUESTS)
    public ApiResponse<PageResponse<GroupPendingRequestResponse>> getPendingRequests(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        String userId = jwt.getSubject();

        return ApiResponse.<PageResponse<GroupPendingRequestResponse>>builder()
                .result(groupPendingRequestService.getPending(roomId, userId, limit))
                .build();
    }

    @Operation(summary = "Lấy danh sách pending request của nhóm")
    @GetMapping("/{roomId}/members")
    @RequireRoomMember
    public ApiResponse<PageResponse<RoomMemberResponse>> getRoomMembers(
            @RoomId @PathVariable String roomId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.<PageResponse<RoomMemberResponse>>builder()
                .result(roomMemberService.getRoomMembers(roomId, cursor, limit))
                .build();
    }

    @Operation(summary = "Phê duyệt hoặc từ chối thành viên vào group")
    @PatchMapping("/{roomId}/pending-requests/{userId}")
    @RequirePermission(MemberPermission.APPROVE_MEMBER)
    public ResponseEntity<Void> approveMember(
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

    @Operation(summary = "Chỉnh sửa quyền member")
    @PatchMapping("/{roomId}/members/{memberId}")
    @RequireAdmin
    public ResponseEntity<Void> updateMember(
            @RoomId @PathVariable String roomId,
            @PathVariable String memberId,
            @RequestBody MemberUpdateRequest request
    ) {
        roomDomainService.updateMember(roomId, memberId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Xoá thành viên ra khỏi nhóm")
    @DeleteMapping("/{roomId}/members/{memberId}")
    @RequirePermission(MemberPermission.REMOVE_MEMBER)
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @PathVariable String memberId
    ) {
        roomDomainService.removeMember(roomId, jwt.getSubject(), memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kiểm tra trạng thái trước khi rời nhóm")
    @PostMapping("/{roomId}/leave-check")
    @RequireRoomMember
    public ApiResponse<LeaveCheckResponse> checkLeave(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId
    ) {
        String userId = jwt.getSubject();
        return ApiResponse.<LeaveCheckResponse>builder()
                .result(roomDomainService.checkLeave(userId, roomId))
                .build();
    }

    @Operation(summary = "Rời nhóm")
    @DeleteMapping("/{roomId}/members/me")
    @RequireRoomMember
    public ResponseEntity<Void> leaveGroup(
            @AuthenticationPrincipal Jwt jwt,
            @RoomId @PathVariable String roomId,
            @RequestParam(required = false) String newAdminId) {
        String userId = jwt.getSubject();
        roomDomainService.leaveGroup(userId, roomId, newAdminId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Giải tán nhóm")
    @DeleteMapping("/{roomId}")
    @RequireAdmin
    public ResponseEntity<Void> disbandGroup(@RoomId @PathVariable String roomId) {
        roomDomainService.disbandGroup(roomId);
        return ResponseEntity.noContent().build();
    }
}
