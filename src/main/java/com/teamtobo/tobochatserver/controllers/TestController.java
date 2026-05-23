package com.teamtobo.tobochatserver.controllers;

import com.google.protobuf.Api;
import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.enums.FriendRequestType;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;
import com.teamtobo.tobochatserver.entities.enums.MemberStatus;
import com.teamtobo.tobochatserver.services.ContactService;
import com.teamtobo.tobochatserver.services.RoomDomainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Test Contact Controller")
@RestController
@RequestMapping("/contacts/test")
@RequiredArgsConstructor
public class TestController {
    private final ContactService contactService;

    private final RoomDomainService roomDomainService;

    @Operation(summary = "Danh sách bạn bè của người dùng")
    @GetMapping("/friends")
    public ApiResponse<PageResponse<FriendResponse>> getFriends(
            @RequestParam String userId,
            @RequestParam(required = false) String roomId,
            @RequestParam(defaultValue = "0", required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {

        PageResponse<FriendResponse> friends = contactService.getFriends(userId, roomId, cursor, limit);
        return ApiResponse.<PageResponse<FriendResponse>>builder()
                .result(friends)
                .build();
    }

    @Operation(summary = "Lấy danh sách lời mời kết bạn")
    @GetMapping("/friend-requests")
    public ApiResponse<PageResponse<FriendRequestResponse>> getFriendRequests(
            @RequestParam String userId,
            @RequestParam(defaultValue = "SENT") FriendRequestType type,
            @RequestParam(defaultValue = "0", required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {

        PageResponse<FriendRequestResponse> friendRequests = contactService.getFriendRequests(type, userId, cursor, limit);
        return ApiResponse.<PageResponse<FriendRequestResponse>>builder()
                .result(friendRequests)
                .build();
    }

    @Operation(summary = "Gửi lời mời kết bạn")
    @PostMapping("/{otherId}")
    public ResponseEntity<Void> sendFriendRequest(
            @RequestParam String userId,
            @PathVariable String otherId) {

        contactService.sendFriendRequest(userId, otherId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Huỷ lời mời")
    @DeleteMapping("/{otherId}")
    public ResponseEntity<Void> cancelFriendRequest(
            @RequestParam String userId,
            @PathVariable String otherId) {

        contactService.cancelFriendRequest(userId, otherId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Trạng thái bạn bè")
    @GetMapping("/{otherId}/friend-status")
    public ApiResponse<FriendStatus> getFriendStatus(
            @RequestParam String userId,
            @PathVariable String otherId) {

        return ApiResponse.<FriendStatus>builder()
                .result(contactService.getFriendStatus(userId, otherId))
                .build();
    }

    @Operation(summary = "Phản hồi lời mời kết bạn (Chấp nhận / Từ chối)")
    @PatchMapping("/response")
    public ResponseEntity<Void> responseFriendRequest(
            @RequestParam String userId,
            @RequestBody FriendAcceptRequest request) {
        contactService.responseFriendRequest(userId, request);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Xoá bạn bè")
    @DeleteMapping("{userId}/friends/{otherId}")
    public ResponseEntity<Void> deleteFriend(
            @PathVariable String userId,
            @PathVariable String otherId) {
        contactService.deleteFriend(userId, otherId);

        return ResponseEntity.noContent().build();
    }

    //-------- Room Domain Service Test ---------

    @Operation(summary = "Thêm thành viên vào nhóm")
    @PostMapping("/members")
    public ResponseEntity<Void> addMember(
            @RequestParam String roomId,
            @RequestParam String userId
            ) {
        roomDomainService.addMemberNeo4j(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Gửi lời mời vào nhóm tới người dùng")
    @PostMapping("/accept-requests")
    public ResponseEntity<Void> inviteMember(
            @RequestParam String roomId,
            @RequestParam String inviterId,
            @RequestParam String targetUserId) {
        roomDomainService.createGroupAcceptRequestNeo4j(roomId, inviterId, targetUserId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Tạo lời mời chờ duyệt cho admin")
    @PostMapping("/pending-requests")
    public ResponseEntity<Void> pendingMember(
            @RequestParam String roomId,
            @RequestParam String inviterId,
            @RequestParam String targetUserId) {
        roomDomainService.createGroupPendingRequestNeo4j(roomId, inviterId, targetUserId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Lấy danh sách ID các phòng mà một User đã tham gia")
    @GetMapping("/joined-rooms")
    public ApiResponse<PageResponse<String>> getJoinedRooms(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0", required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        PageResponse<String> roomIds = roomDomainService.getJoinedRoomIdsNeo4j(userId, cursor, limit);
        return ApiResponse.<PageResponse<String>>builder()
                .result(roomIds)
                .build();
    }

    @Operation(summary = "Danh sách người dùng cần duyệt vào nhóm")
    @GetMapping("/pending-requests")
    public ApiResponse<PageResponse<GroupPendingRequestResponse>> getPendingRequests(
            @RequestParam String userId,
            @RequestParam String roomId,
            @RequestParam(defaultValue = "0", required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        PageResponse<GroupPendingRequestResponse> pendingRequests =
                roomDomainService.getPendingRequests(roomId, userId, cursor, limit);
        return ApiResponse.<PageResponse<GroupPendingRequestResponse>>builder()
                .result(pendingRequests)
                .build();
    }

    @Operation(summary = "Danh sách lời mời vào nhóm")
    @GetMapping("/accept-requests")
    public ApiResponse<PageResponse<GroupAcceptRequestResponse>> getAcceptRequests(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0", required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        PageResponse<GroupAcceptRequestResponse> acceptRequests =
                roomDomainService.getAcceptRequests(userId, cursor, limit);
        return ApiResponse.<PageResponse<GroupAcceptRequestResponse>>builder()
                .result(acceptRequests)
                .build();
    }

    @Operation(summary = "Lấy trạng thái mối quan hệ hiện tại giữa User và Phòng")
    @GetMapping("/member-status")
    public ResponseEntity<MemberStatus> getMemberStatus(
            @RequestParam String roomId,
            @RequestParam String userId) {
        MemberStatus status = roomDomainService.getMemberStatusNeo4j(roomId, userId);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Xóa bỏ mọi mối quan hệ (Rời phòng / Hủy lời mời / Từ chối duyệt) giữa User và Room")
    @DeleteMapping("/relationships")
    public ResponseEntity<Void> deleteRelationship(
            @RequestParam String roomId,
            @RequestParam String userId) {
        roomDomainService.deleteMemberRelationshipNeo4j(roomId, userId);
        return ResponseEntity.ok().build();
    }
}
