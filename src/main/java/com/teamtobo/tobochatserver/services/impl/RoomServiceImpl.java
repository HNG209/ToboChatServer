package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.request.CreateRoomRequest;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final DynamoDbTable<Room> roomTable;
    private final DynamoDbTable<RoomMember> roomMemberTable;
    private final DynamoDbTable<User> userTable;
    private final DynamoDbEnhancedClient enhancedClient;

    private User getUserById(String userId) {
        User user = userTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("PROFILE")
                .build());

        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public RoomResponse createRoom(String userId, CreateRoomRequest request) {
        RoomType roomType = RoomType.valueOf(request.getType().toUpperCase());

        if (roomType == RoomType.DM) {
            return createDmRoom(userId, request.getFriendId());
        } else {
            return createGroupRoom(userId, request);
        }
    }

    private RoomResponse createDmRoom(String userId, String friendId) {
        // Lấy thông tin bạn bè để lưu denormalized
        User friend = getUserById(friendId);
        User currentUser = getUserById(userId);

        String roomId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        // Tạo Room entity
        Room room = Room.builder()
                .pk("ROOM#" + roomId)
                .type(RoomType.DM.name())
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Tạo RoomMember cho người dùng hiện tại (lưu thông tin bạn bè vào đây)
        RoomMember memberForCurrentUser = RoomMember.builder()
                .pk("USER#" + userId)
                .sk("ROOM#" + roomId)
                .gsi1pk("ROOM#" + roomId)
                .gsi1sk("USER#" + userId)
                .roomType(RoomType.DM.name())
                .friendId("USER#" + friendId)
                .friendName(friend.getName())
                .friendAvatarUrl(friend.getAvatarUrl())
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Tạo RoomMember cho bạn bè (lưu thông tin người dùng hiện tại vào đây)
        RoomMember memberForFriend = RoomMember.builder()
                .pk("USER#" + friendId)
                .sk("ROOM#" + roomId)
                .gsi1pk("ROOM#" + roomId)
                .gsi1sk("USER#" + friendId)
                .roomType(RoomType.DM.name())
                .friendId("USER#" + userId)
                .friendName(currentUser.getName())
                .friendAvatarUrl(currentUser.getAvatarUrl())
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Lưu tất cả cùng lúc bằng transaction
        enhancedClient.transactWriteItems(b -> b
                .addPutItem(roomTable, room)
                .addPutItem(roomMemberTable, memberForCurrentUser)
                .addPutItem(roomMemberTable, memberForFriend)
        );

        return RoomResponse.builder()
                .id(room.getPk())
                .type(RoomType.DM.name())
                .friendId(memberForCurrentUser.getFriendId())
                .friendName(friend.getName())
                .friendAvatarUrl(friend.getAvatarUrl())
                .createdAt(now)
                .build();
    }

    private RoomResponse createGroupRoom(String userId, CreateRoomRequest request) {
        String roomId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Room room = Room.builder()
                .pk("ROOM#" + roomId)
                .type(RoomType.GROUP.name())
                .name(request.getName())
                .createdAt(now)
                .updatedAt(now)
                .build();

        RoomMember member = RoomMember.builder()
                .pk("USER#" + userId)
                .sk("ROOM#" + roomId)
                .gsi1pk("ROOM#" + roomId)
                .gsi1sk("USER#" + userId)
                .roomType(RoomType.GROUP.name())
                .createdAt(now)
                .updatedAt(now)
                .build();

        enhancedClient.transactWriteItems(b -> b
                .addPutItem(roomTable, room)
                .addPutItem(roomMemberTable, member)
        );

        return RoomResponse.builder()
                .id(room.getPk())
                .type(RoomType.GROUP.name())
                .name(room.getName())
                .createdAt(now)
                .build();
    }

    @Override
    public PageResponse<RoomResponse> getRooms(String userId, String cursor, int limit) {
        String pk = "USER#" + userId;

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(
                        QueryConditional.sortBeginsWith(
                                Key.builder()
                                        .partitionValue(pk)
                                        .sortValue("ROOM#")
                                        .build()
                        )
                )
                .limit(limit);

        if (cursor != null) {
            Map<String, AttributeValue> exclusiveStartKey = Map.of(
                    "pk", AttributeValue.builder().s(pk).build(),
                    "sk", AttributeValue.builder().s(cursor).build()
            );
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        Page<RoomMember> page = roomMemberTable
                .query(requestBuilder.build())
                .stream()
                .findFirst()
                .orElse(null);

        if (page == null) {
            return PageResponse.<RoomResponse>builder()
                    .items(List.of())
                    .nextCursor(null)
                    .build();
        }

        String nextCursor = null;
        if (page.lastEvaluatedKey() != null && page.lastEvaluatedKey().get("sk") != null) {
            nextCursor = page.lastEvaluatedKey().get("sk").s();
        }

        List<RoomResponse> rooms = page.items().stream()
                .map(this::toRoomResponse)
                .toList();

        return PageResponse.<RoomResponse>builder()
                .items(rooms)
                .nextCursor(nextCursor)
                .build();
    }

    @Override
    public RoomResponse getRoom(String userId, String roomId) {
        RoomMember member = roomMemberTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("ROOM#" + roomId)
                .build());

        if (member == null) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        return toRoomResponse(member);
    }

    private RoomResponse toRoomResponse(RoomMember member) {
        // Lấy roomId từ SK: ROOM#{roomId}
        String roomPk = member.getSk(); // e.g. "ROOM#abc123"
        String roomId = Helper.normalizeId(roomPk);

        RoomResponse.RoomResponseBuilder builder = RoomResponse.builder()
                .id(roomPk)
                .type(member.getRoomType())
                .createdAt(member.getCreatedAt());

        if (RoomType.DM.name().equals(member.getRoomType())) {
            // Hiển thị thông tin bạn bè được lưu trong RoomMember
            builder
                    .friendId(member.getFriendId())
                    .friendName(member.getFriendName())
                    .friendAvatarUrl(member.getFriendAvatarUrl());
        } else {
            // Lấy thông tin Group từ Room entity
            Room room = roomTable.getItem(Key.builder()
                    .partitionValue("ROOM#" + roomId)
                    .sortValue("METADATA")
                    .build());

            if (room != null) {
                builder
                        .name(room.getName())
                        .avatarUrl(room.getAvatarUrl())
                        .lastMessageAt(room.getLastMessageAt());
            }
        }

        return builder.build();
    }
}
