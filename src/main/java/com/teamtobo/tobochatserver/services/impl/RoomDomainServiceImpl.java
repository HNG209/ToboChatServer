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

                // 2. Chuẩn bị list
                List<String> acceptedMembers = new ArrayList<>();
                List<String> pendingMembers = new ArrayList<>();

                // 3. Filter
                for (String memberId : uniqueMembers) {

                    // luôn giữ người tạo
                    if (memberId.equals(userId)) {
                        acceptedMembers.add(memberId);
                        continue;
                    }

                    // check friend
                    if (userService.getFriendStatus(userId, memberId) != FriendStatus.FRIEND) {
                        throw new AppException(ErrorCode.ONLY_FRIEND_CAN_ADD);
                    }

                    // check setting
                    User user = userService.getUserById(memberId);

                    if (user.isAllowAutoAddToGroup()) {
                        acceptedMembers.add(memberId);
                    } else {
                        pendingMembers.add(memberId);
                    }
                }

                // 4. Validate lại sau filter
//                if (acceptedMembers.size() < 3) {
//                    throw new AppException(ErrorCode.GROUP_SIZE_INVALID);
//                }

                // 5. Tạo roomId
                String roomId = UUID.randomUUID().toString();

                // 6. Lưu room (chỉ acceptedMembers)
                saveRoomToDynamoDB(
                        userId,
                        roomId,
                        request.getRoomName(),
                        roomType,
                        acceptedMembers,
                        now
                );

                // 7. Tạo request cho pending
                for (String pendingUserId : pendingMembers) {
                    GroupAcceptRequest req = GroupAcceptRequest.builder()
                            .pk("USER#" + pendingUserId)
                            .sk("ROOM_ACCEPT#" + roomId)
                            .roomId(roomId)
                            .inviterId(userId)
                            .roomName(request.getRoomName())
                            .build();

                    try {
                        groupAcceptRequestTable.putItem(req);
                    } catch (Exception e) {
                        throw new AppException(ErrorCode.ROOM_CREATE_ERROR);
                    }
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
            // Người tạo nhóm là Admin
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
                    .build();

            txBuilder.addPutItem(roomMemberTable, member);
        }

        try {
            enhancedClient.transactWriteItems(txBuilder.build());
        } catch (Exception e) {
            throw new AppException(ErrorCode.ROOM_CREATE_ERROR);
        }
    }
}
