package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
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
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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
    @Override
            public void createRoom(String userId, RoomCreateRequest request, RoomType roomType) {
        List<String> memberIds = new ArrayList<>(request.getMemberIds());
        memberIds.add(userId);
        // Loại bỏ các ID trùng lặp
        List<String> uniqueMembers = memberIds.stream()
                .distinct()
                .collect(Collectors.toList());
        // Kiểm tra các user có tồn tại
        uniqueMembers.forEach(userService::getUserProfile);

        String now = Instant.now().toString(); // Dùng chuẩn ISO 8601 cho thời gian

        switch (roomType) {
            case DM -> {
                // 1. Validate DM
                if (uniqueMembers.size() != 2) {
                    throw new AppException(ErrorCode.ROOM_INVALID);
                }

                // 2. Tạo Deterministic ID
                Collections.sort(uniqueMembers);
                String roomId = uniqueMembers.get(0) + "_" + uniqueMembers.get(1);

                // Kiểm tra xem phòng DM này đã tồn tại chưa
                Room existedRoom = roomService.getRoomById(roomId, true);
                if (existedRoom != null) return;

                // DM room không cần tên cụ thể
                String roomName = "Direct Message";

                // 3. Ghi xuống DB
                saveRoomToDynamoDB(userId, roomId, roomName, roomType, uniqueMembers, now);
            }

            case GROUP -> {
                // 1. Validate Group
                if (uniqueMembers.size() < 3) {
                    throw new AppException(ErrorCode.GROUP_SIZE_INVALID);
                }

                if (request.getRoomName() == null || request.getRoomName().trim().isEmpty()) {
                    throw new AppException(ErrorCode.ROOM_NAME_REQUIRED);
                }

                String roomId = UUID.randomUUID().toString();

                saveRoomToDynamoDB(
                        userId,
                        roomId,
                        request.getRoomName(),
                        roomType,
                        List.of(userId),
                        now
                );

                Room room = roomService.getRoomById(roomId, true);
                RoomMember creator = getMember(roomId, userId);

                for (String memberId : uniqueMembers) {
                    if (memberId.equals(userId)) continue;

                    // check friend
                    if (userService.getFriendStatus(userId, memberId) != FriendStatus.FRIEND) {
                        throw new AppException(ErrorCode.ONLY_FRIEND_CAN_ADD);
                    }

                    User targetUser = userService.getUserById(memberId);

                    if (isMember(roomId, memberId)) continue;

                    handleAddMember(room, creator, targetUser, memberId);
                }
            }
        }
    }
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

    @Override
    public void addMemberToGroup(String roomId, String inviterId, List<String> targetUserIds) {

        Room room = roomService.getRoomById(roomId, true);

        if (room.getRoomType() != RoomType.GROUP) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

        RoomMember inviter = getMember(roomId, inviterId);

        for (String targetUserId : targetUserIds) {

            if (isMember(roomId, targetUserId)) continue;

            validateFriend(inviterId, targetUserId);

            User targetUser = userService.getUserById(targetUserId);

            // === TRUYỀN THÊM targetUserId vào ===
            handleAddMember(room, inviter, targetUser, targetUserId);
        }
    }

    // =========================
// CORE LOGIC (ĐÃ SỬA - DÙNG targetUserId trực tiếp)
// =========================
    private void handleAddMember(Room room, RoomMember inviter, User targetUser, String targetUserId) {

        String roomId = room.getPk().replace("ROOM#", "");
        String inviterId = inviter.getMemberId();
        String roomName = room.getRoomName();

        //IF1: Nhóm có xét duyệt không?
        if (room.isApproveMember()) {

            //IF2: Inviter có phải Admin không?
            if (inviter.getRole() != MemberRole.ADMIN) {
                // Thành viên thường thêm người thì phải chờ duyệt (Pending)
                createGroupPendingRequest(roomId, inviterId, targetUserId, roomName);
                return;
            }
            //Nếu là Admin thì tiếp tục xử lý bình thường
        }


        //IF3: B có cho phép tự động thêm vào group?
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
