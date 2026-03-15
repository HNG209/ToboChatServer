package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.Friend;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.RoomService;
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
    private final DynamoDbTable<Friend> friendTable;
    private final DynamoDbEnhancedClient enhancedClient;

    @Override
    public RoomResponse createOrGetDmRoom(String userId, String friendId) {
        // 0. Kiểm tra không thể tạo DM với chính mình
        if (userId.equals(friendId)) {
            throw new AppException(ErrorCode.CANNOT_ADD_SELF);
        }

        // 1. Kiểm tra xem đã là bạn bè chưa
        Friend friendship = friendTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("FRIEND#" + friendId)
                .build());
        if (friendship == null) {
            throw new AppException(ErrorCode.NOT_FRIENDS);
        }

        // 2. Kiểm tra phòng DM đã tồn tại chưa (dựa trên RoomMember của userId có friendId trùng)
        //    SK = "ROOM_DM#{friendId}" để tra nhanh mà không cần scan
        RoomMember existingMembership = roomMemberTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("ROOM_DM#" + friendId)
                .build());

        if (existingMembership != null) {
            // Phòng đã tồn tại -> trả về thông tin phòng kèm thông tin bạn bè
            return toRoomResponse(existingMembership);
        }

        // 3. Lấy thông tin của cả 2 người để lưu vào RoomMember
        User currentUser = getUserById(userId);
        User friend = getUserById(friendId);

        // 4. Tạo roomId mới
        String roomId = UUID.randomUUID().toString();
        String roomPk = "ROOM#" + roomId;

        // 5. Ghi 3 bản ghi vào DynamoDB trong 1 transaction:
        //    - Room metadata
        //    - RoomMember cho userId (lưu thông tin của friendId để hiển thị)
        //    - RoomMember cho friendId (lưu thông tin của userId để hiển thị)
        Room room = Room.builder()
                .pk(roomPk)
                .type(RoomType.DM.name())
                .build();

        // Với DM: SK dạng "ROOM_DM#{otherUserId}" để có thể tra nhanh phòng DM đã tồn tại
        RoomMember memberForUser = RoomMember.builder()
                .pk("USER#" + userId)
                .sk("ROOM_DM#" + friendId)
                .type(RoomType.DM.name())
                .roomId(roomId)
                .friendId("USER#" + friendId)
                .name(friend.getName())
                .avatarUrl(friend.getAvatarUrl())
                .build();

        RoomMember memberForFriend = RoomMember.builder()
                .pk("USER#" + friendId)
                .sk("ROOM_DM#" + userId)
                .type(RoomType.DM.name())
                .roomId(roomId)
                .friendId("USER#" + userId)
                .name(currentUser.getName())
                .avatarUrl(currentUser.getAvatarUrl())
                .build();

        enhancedClient.transactWriteItems(b -> b
                .addPutItem(roomTable, room)
                .addPutItem(roomMemberTable, memberForUser)
                .addPutItem(roomMemberTable, memberForFriend)
        );

        return toRoomResponse(memberForUser);
    }

    @Override
    public PageResponse<RoomResponse> getUserRooms(String userId, String cursor, int limit) {
        String pk = "USER#" + userId;

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(
                        QueryConditional.sortBeginsWith(
                                Key.builder()
                                        .partitionValue(pk)
                                        .sortValue("ROOM_")
                                        .build()
                        )
                )
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
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
                    .build();
        }

        String nextCursor = null;
        if (page.lastEvaluatedKey() != null && page.lastEvaluatedKey().get("sk") != null) {
            nextCursor = page.lastEvaluatedKey().get("sk").s();
        }

        return PageResponse.<RoomResponse>builder()
                .items(page.items().stream().map(this::toRoomResponse).toList())
                .nextCursor(nextCursor)
                .build();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────────

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

    /**
     * Chuyển RoomMember thành RoomResponse.
     *
     * Đây là nơi giải quyết bài toán hiển thị thông tin bạn bè trong DM:
     * - name và avatarUrl trong RoomMember đã được lưu sẵn là thông tin của người bạn (khi tạo phòng)
     * - Chỉ cần map thẳng vào RoomResponse mà không cần query thêm
     */
    private RoomResponse toRoomResponse(RoomMember member) {
        return RoomResponse.builder()
                .id(member.getRoomId())           // UUID thực của Room
                .type(member.getType())
                .name(member.getName())           // Với DM: tên bạn bè
                .avatarUrl(member.getAvatarUrl()) // Với DM: ảnh bạn bè
                .friendId(member.getFriendId())   // Với DM: "USER#{friendId}"
                .createdAt(member.getCreatedAt())
                .build();
    }
}
