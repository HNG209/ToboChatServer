package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.entities.*;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class RoomDomainServiceImpl implements RoomDomainService {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Room> roomTable;
    private final DynamoDbTable<RoomMember> roomMemberTable;

    private final UserService userService;
    private final RoomService roomService;

    private final DynamoDbTable<GroupAcceptRequest> groupAcceptRequestTable;
    private final DynamoDbTable<GroupPendingRequest> groupPendingRequestTable;

    // Tạo nhóm và add member
    @Override
    public void createRoom(String userId, RoomCreateRequest request, RoomType roomType) {
        List<String> members = prepareMembers(userId, request);

        switch (roomType) {
            case DM -> createDMRoom(userId, members);
            case GROUP -> createGroupRoom(userId, request, members);
        }
    }

    @Override
    public void approveMember(String roomId, String adminId, String targetUserId, boolean accept) {

        Room room = roomService.getRoomById(roomId, true);

        //phải là group
        if (room.getRoomType() != RoomType.GROUP) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

        //group có bật duyệt không
        if (!room.isApproveMember()) {
            throw new AppException(ErrorCode.ROOM_NOT_REQUIRE_APPROVAL);
        }

        String pk = "ROOM#" + roomId;
        String sk = "PENDING#" + targetUserId;

        GroupPendingRequest pending = groupPendingRequestTable.getItem(
                Key.builder().partitionValue(pk).sortValue(sk).build()
        );

        if (pending == null) {
            throw new AppException(ErrorCode.PENDING_REQUEST_NOT_FOUND);
        }

        //xoá pending trước
        groupPendingRequestTable.deleteItem(
                Key.builder().partitionValue(pk).sortValue(sk).build()
        );

        //reject
        if (!accept) return;

        //tránh add trùng
        if (isMember(roomId, targetUserId)) return;

        User targetUser = userService.getUserById(targetUserId);

        //tránh tạo accept request trùng
        GroupAcceptRequest existed = groupAcceptRequestTable.getItem(
                Key.builder()
                        .partitionValue("USER#" + targetUserId)
                        .sortValue("ROOM_ACCEPT#" + roomId)
                        .build()
        );

        if (existed != null) return;

        if (targetUser.isAllowAutoAddToGroup()) {
            addMember(roomId, targetUserId, room.getRoomName());
        } else {
            createGroupAcceptRequest(roomId, adminId, targetUserId, room.getRoomName());
        }
    }

    @Override
    public void toggleApproveMember(String roomId, String userId) {

        Room room = roomService.getRoomById(roomId, true);

        RoomMember member = getMember(roomId, userId);
        if (member.getRole() != MemberRole.ADMIN) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        boolean newValue = !room.isApproveMember();

        // nếu đang bật mà muốn tắt thì check pending
        if (room.isApproveMember() && !newValue) {
            boolean hasPending = groupPendingRequestTable.query(
                    QueryEnhancedRequest.builder()
                            .queryConditional(
                                    QueryConditional.sortBeginsWith(
                                            Key.builder()
                                                    .partitionValue("ROOM#" + roomId)
                                                    .sortValue("PENDING#")
                                                    .build()
                                    )
                            )
                            .limit(1)
                            .build()
            ).items().stream().findAny().isPresent();

            if (hasPending) {
                throw new AppException(ErrorCode.CANNOT_DISABLE_APPROVAL_WHEN_PENDING);
            }
        }

        room.setApproveMember(newValue);
        roomTable.putItem(room);
    }

    @Override
    public void toggleAllowAddMember(String roomId, String userId) {
        Room room = roomService.getRoomById(roomId, true);
        RoomMember member = getMember(roomId, userId);
        if (room.getRoomType() != RoomType.GROUP) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

        if (member.getRole() != MemberRole.ADMIN) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        room.setAllowAddMember(!room.isAllowAddMember());
        roomTable.updateItem(room);
    }

    @Override
    public void toggleAllowSendMessage(String roomId, String userId) {
        Room room = roomService.getRoomById(roomId, true);
        RoomMember member = getMember(roomId, userId);
        if (room.getRoomType() != RoomType.GROUP) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

        if (member.getRole() != MemberRole.ADMIN) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        room.setAllowSendMessage(!room.isAllowSendMessage());
        roomTable.updateItem(room);
    }

    @Override
    public void toggleAllowUpdateGroup(String roomId, String userId) {
        Room room = roomService.getRoomById(roomId, true);
        RoomMember member = getMember(roomId, userId);
        if (room.getRoomType() != RoomType.GROUP) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

        if (member.getRole() != MemberRole.ADMIN) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        room.setAllowUpdateMetadata(!room.isAllowUpdateMetadata());
        roomTable.updateItem(room);
    }


    // Add member khi đã tạo nhóm
    @Override
    public void addMemberToGroup(String roomId, String inviterId, List<String> targetUserIds) {

        Room room = roomService.getRoomById(roomId, true);

        if (room.getRoomType() != RoomType.GROUP)
            throw new AppException(ErrorCode.ROOM_INVALID);

        RoomMember inviter = getMember(roomId, inviterId);

        if(inviter.getRole() != MemberRole.ADMIN && !room.isAllowAddMember())
            throw new AppException(ErrorCode.ADD_MEMBER_NOT_ALLOWED);

        for (String targetUserId : targetUserIds) {

            if (isMember(roomId, targetUserId)) continue;

            validateFriend(inviterId, targetUserId);

            User targetUser = userService.getUserById(targetUserId);

            handleAddMember(room, inviter, targetUser, targetUserId);
        }
    }

    @Override
    public void addViceAdmin(String roomId, String adminId, String targetUserId) {
        Room room = roomService.getRoomById(roomId, true);

        if (room.getRoomType() != RoomType.GROUP) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }
        RoomMember admin = getMember(roomId, adminId);

        if (admin.getRole() != MemberRole.ADMIN) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        RoomMember targetMember = getMember(roomId, targetUserId);

        targetMember.setRole(MemberRole.VICE_ADMIN);
        targetMember.setUpdatedAt(Instant.now().toString());

        roomMemberTable.updateItem(targetMember);
    }

    @Override
    public void removeMember(String roomId, String removerId, String targetUserId) {
        Room room = roomService.getRoomById(roomId, true);

        if (room.getRoomType() != RoomType.GROUP) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }
        RoomMember remover = getMember(roomId, removerId);
        RoomMember target = getMember(roomId, targetUserId);

        if (remover.getRole() == MemberRole.MEMBER) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        if (remover.getRole() == MemberRole.VICE_ADMIN && target.getRole() != MemberRole.MEMBER) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        roomMemberTable.deleteItem(target);
    }

    @Override
    public void leaveGroup(String roomId, String userId) {
        Room room = roomService.getRoomById(roomId, true);
        if (room.getRoomType() != RoomType.GROUP) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }
        RoomMember member = getMember(roomId, userId);
        roomMemberTable.deleteItem(member);
    }

    @Override
    public String getOrCreateDMRoom(String userId, String otherId) {
        List<String> members = Stream.of(userId, otherId)
                .distinct()
                .sorted()
                .toList();

        if (members.size() != 2) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

        String roomId = members.get(0) + "_" + members.get(1);

        Room existed = roomService.getRoomById(roomId, true);
        if (existed != null) return roomId;

        // gọi lại logic createRoom cũ
        createRoom(userId,
                RoomCreateRequest.builder()
                        .memberIds(List.of(otherId))
                        .build(),
                RoomType.DM
        );

        return roomId;
    }
    private void createDMRoom(String userId, List<String> members) { // Tạo phòng DM
        if (members.size() != 2) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

        List<String> sorted = members.stream().sorted().toList();
        String roomId = sorted.get(0) + "_" + sorted.get(1);

        Room existed = roomService.getRoomById(roomId, true);
        if (existed != null) return;

        String now = Instant.now().toString();
        String pk = "ROOM#" + roomId;

        String otherId = sorted.stream()
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElseThrow();

        // check friend
        FriendStatus friendStatus = userService.getFriendStatus(userId, otherId);

        InboxStatus senderStatus = InboxStatus.ACTIVE;
        InboxStatus receiverStatus =
                (friendStatus == FriendStatus.FRIEND)
                        ? InboxStatus.ACTIVE
                        : InboxStatus.PENDING;

        TransactWriteItemsEnhancedRequest.Builder tx =
                TransactWriteItemsEnhancedRequest.builder();

        // 1. Room metadata
        tx.addPutItem(roomTable, Room.builder()
                .pk(pk)
                .roomType(RoomType.DM)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 2. Sender
        tx.addPutItem(roomMemberTable, buildMember(
                roomId,
                userId,
                "Direct Message",
                MemberRole.MEMBER,
                now,
                senderStatus
        ));

        // 3. Receiver
        tx.addPutItem(roomMemberTable, buildMember(
                roomId,
                otherId,
                "Direct Message",
                MemberRole.MEMBER,
                now,
                receiverStatus
        ));

        enhancedClient.transactWriteItems(tx.build());
    }

//    private void createDMRoom(String userId, List<String> members) {
//        // validate
//        if (members.size() != 2) {
//            throw new AppException(ErrorCode.ROOM_INVALID);
//        }
//
//        // deterministic ID
//        List<String> sorted = members.stream().sorted().toList();
//        String roomId = sorted.get(0) + "_" + sorted.get(1);
//
//        // check tồn tại
//        Room existed = roomService.getRoomById(roomId, true);
//        if (existed != null) return;
//
//        String now = Instant.now().toString();
//
//        saveRoomToDynamoDB(
//                userId,
//                roomId,
//                "Direct Message",
//                RoomType.DM,
//                sorted,
//                now
//        );
//    }

    private void createGroupRoom(String userId, RoomCreateRequest request, List<String> members) {

        // validate
        if (members.size() < 2) {
            throw new AppException(ErrorCode.GROUP_SIZE_INVALID);
        }

        if (request.getRoomName() == null || request.getRoomName().trim().isEmpty()) {
            throw new AppException(ErrorCode.ROOM_NAME_REQUIRED);
        }

        String roomId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        // tạo room + creator trước
        saveRoomToDynamoDB(
                userId,
                roomId,
                request.getRoomName(),
                RoomType.GROUP,
                List.of(userId),
                now
        );

        Room room = roomService.getRoomById(roomId, true);
        RoomMember creator = getMember(roomId, userId);

        // add members còn lại
        for (String memberId : members) {
            if (memberId.equals(userId)) continue;

            validateFriend(userId, memberId);

            if (isMember(roomId, memberId)) continue;

            User targetUser = userService.getUserById(memberId);

            handleAddMember(room, creator, targetUser, memberId);
        }
    }

    private List<String> prepareMembers(String userId, RoomCreateRequest request) {
        List<String> memberIds = new ArrayList<>(request.getMemberIds());
        memberIds.add(userId);

        return memberIds.stream().distinct().toList();
    }

    // Lưu Room (metadata) + RoomMember
    private void saveRoomToDynamoDB(
            String userId,
            String roomId,
            String roomName,
            RoomType type,
            List<String> memberIds,
            String now
    ) {
        String pk = "ROOM#" + roomId;

        TransactWriteItemsEnhancedRequest.Builder txBuilder =
                TransactWriteItemsEnhancedRequest.builder();

        Room roomMetadata = Room.builder()
                .pk(pk)
                .roomName(roomName)
                .allowAddMember(true)
                .allowUpdateMetadata(true)
                .allowSendMessage(true)
                .roomType(type)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Lưu metadata trước
        txBuilder.addPutItem(roomTable, roomMetadata);

        for (String memberId : memberIds) {
            boolean isAdmin = (Objects.equals(memberId, userId) && type == RoomType.GROUP);

            String sk = "MEMBER#" + memberId;

            RoomMember member = RoomMember.builder()
                    .pk(pk)
                    .sk(sk)
                    .role(isAdmin ? MemberRole.ADMIN : MemberRole.MEMBER)
                    .status(InboxStatus.ACTIVE)
                    .roomName(roomName)
                    .lastActivityAt(now)
                    .roomType(type)
                    .createdAt(now)
                    .updatedAt(now)
                    .statusTime("STATUS#ACTIVE#TIME#" + now)
                    .addedBy(type == RoomType.GROUP ? userId : null)
                    .build();

            txBuilder.addPutItem(roomMemberTable, member);
        }

        try {
            enhancedClient.transactWriteItems(txBuilder.build());
        } catch (Exception e) {
            throw new AppException(ErrorCode.ROOM_CREATE_ERROR);
        }
    }
    private RoomMember buildMember(
            String roomId,
            String userId,
            String roomName,
            MemberRole role,
            String now,
            InboxStatus status
    ) {
        return RoomMember.builder()
                .pk("ROOM#" + roomId)
                .sk("MEMBER#" + userId)
                .role(role)
                .status(status)
                .roomName(roomName)
                .lastActivityAt(now)
                .createdAt(now)
                .updatedAt(now)
                .statusTime("STATUS#" + status + "#TIME#" + now)
                .build();
    }

    // Kiểm tra và thêm các thành viên khác vào nhóm (tạo RoomMember hoặc GroupPendingRequest, GroupAcceptRequest)
    // Chỉ sử dụng cho roomType = GROUP
    private void handleAddMember(Room room, RoomMember inviter, User targetUser, String targetUserId) {

        String roomId = room.getPk().replace("ROOM#", "");
        String inviterId = inviter.getMemberId();
        String roomName = room.getRoomName();

        // Nhóm có xét duyệt không?
        if (room.isApproveMember()) {
            // Inviter có phải Admin không?
            if (inviter.getRole() != MemberRole.ADMIN) {
                // Thành viên thường thêm người thì phải chờ duyệt (Pending)
                createGroupPendingRequest(roomId, inviterId, targetUserId, roomName);
                return;
            }
        }

        // B có cho phép tự động thêm vào group?
        if (targetUser.isAllowAutoAddToGroup()) {
            addMember(roomId, targetUserId, roomName);
        } else {
            createGroupAcceptRequest(roomId, inviterId, targetUserId, roomName);
        }
    }

    private RoomMember getMember(String roomId, String userId) {
        RoomMember member = roomMemberTable.getItem(
                Key.builder()
                        .partitionValue("ROOM#" + roomId)
                        .sortValue("MEMBER#" + userId)
                        .build()
        );

        if (member == null) {
            throw new AppException(ErrorCode.NOT_IN_ROOM);
        }
        return member;
    }

    private boolean isMember(String roomId, String userId) {
        return roomMemberTable.getItem(
                Key.builder()
                        .partitionValue("ROOM#" + roomId)
                        .sortValue("MEMBER#" + userId)
                        .build()
        ) != null;
    }

    private void validateFriend(String inviterId, String targetUserId) {
        if (userService.getFriendStatus(inviterId, targetUserId) != FriendStatus.FRIEND) {
            throw new AppException(ErrorCode.ONLY_FRIEND_CAN_ADD);
        }
    }

    private void addMember(String roomId, String userId, String roomName) {
        String now = Instant.now().toString();

        RoomMember member = RoomMember.builder()
                .pk("ROOM#" + roomId)
                .sk("MEMBER#" + userId)
                .role(MemberRole.MEMBER)
                .status(InboxStatus.ACTIVE)
                .roomType(RoomType.GROUP)
                .roomName(roomName)
                .lastActivityAt(now)
                .createdAt(now)
                .updatedAt(now)
                .statusTime("STATUS#ACTIVE#TIME#" + now)
                .build();

        roomMemberTable.putItem(member);
    }

    private void createGroupAcceptRequest(String roomId, String inviterId, String targetUserId, String roomName) {
        groupAcceptRequestTable.putItem(
                GroupAcceptRequest.builder()
                        .pk("USER#" + targetUserId)
                        .sk("ROOM_ACCEPT#" + roomId)
                        .roomId(roomId)
                        .inviterId(inviterId)
                        .roomName(roomName)
                        .build()
        );
    }

    private void createGroupPendingRequest(String roomId, String inviterId, String targetUserId, String roomName) {
        groupPendingRequestTable.putItem(
                GroupPendingRequest.builder()
                        .pk("ROOM#" + roomId)
                        .sk("PENDING#" + targetUserId)
                        .roomId(roomId)
                        .userId(targetUserId)
                        .requesterId(inviterId)
                        .roomName(roomName)
                        .build()
        );
    }
}
