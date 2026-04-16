package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.*;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomMemberServiceImpl implements RoomMemberService {
    private final DynamoDbTable<RoomMember> roomMemberTable;
    private final RoomService roomService;
    private final UserService userService;
    private final ChatService chatService;

    /**
     * Lấy danh sách phòng đã tham gia của user với pagination
     * @param userId
     * @param cursor
     * @param limit
     * @return
     */
    // Deprecated
    private final DynamoDbClient lowLevelClient;
    private final SocketIOServer socketIOServer;

    @Override
    public void increaseUnreadCount(String senderId, String roomId) {
        String cleanSenderId = Helper.normalizeId(senderId);
        String cleanRoomId = Helper.normalizeId(roomId);

        List<String> memberIds = roomService.getMembersByRoomId(roomId);

        for (String memberId: memberIds) {
            String cleanMemberId = Helper.normalizeId(memberId);
            if (cleanMemberId.equals(cleanSenderId)) continue;

            updateCounter(Map.of("pk", AttributeValue.builder().s("ROOM#" + cleanRoomId).build(),
                        "sk", AttributeValue.builder().s("MEMBER#" + cleanMemberId).build()),
                    "unreadMessages", 1);

            updateCounter(Map.of("pk", AttributeValue.builder().s("USER#" + cleanMemberId).build(),
                    "sk", AttributeValue.builder().s("PROFILE").build()),
                    "totalUnreadMessages", 1);

        }

    }

    @Override
    public void markAsReadedMessage(String userId, String roomId) {
        String cleanRoomId = Helper.normalizeId(roomId);
        String cleanUserId = Helper.normalizeId(userId);

        RoomMember member = roomMemberTable.getItem(Key.builder()
                .partitionValue("ROOM#" + cleanRoomId)
                .sortValue(("MEMBER#" + cleanUserId))
                .build());

        if (member != null && member.getUnreadMessages() > 0) {
            int countToReduce = member.getUnreadMessages();

            updateCounter(Map.of("pk", AttributeValue.builder().s("USER#" + cleanUserId).build(),
                    "sk", AttributeValue.builder().s("PROFILE").build()),
                    "totalUnreadMessages", -countToReduce);

            member.setUnreadMessages(0);
            roomMemberTable.updateItem(member);

            int updatedTotal = userService.getUserProfile(userId).getTotalUnreadMessages();
            socketIOServer.getRoomOperations(userId).sendEvent("mark_read_update", Map.of(
                    "roomId", roomId,
                    "newTotalUnread", updatedTotal
            ));

        }
    }

    private void updateCounter(Map<String, AttributeValue> key, String attributeName, int value) {
        lowLevelClient.updateItem(u -> u.tableName("ToboChatTable")
                .key(key)
                .updateExpression("ADD " + attributeName + " :val")
                .expressionAttributeValues(Map.of(":val", AttributeValue.builder()
                        .n(String.valueOf(value))
                        .build()))
        );
    }

    @Override
    public PageResponse<RoomResponse> getJoinedRooms(String userId, String cursor, int limit, InboxStatus status) {
        String gsiPartitionKey = "MEMBER#" + userId;
        String gsiSortKeyPrefix = "STATUS#" + status.name() + "#TIME#";

        DynamoDbIndex<RoomMember> index = roomMemberTable.index("GSI_ChatInbox");

        QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(gsiPartitionKey)
                        .sortValue(gsiSortKeyPrefix)
                        .build()
        );

        QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false) // Mới nhất lên đầu
                .limit(limit);

        // ==========================================
        // 1. GIẢI MÃ CURSOR TỪ FRONTEND (Decode Base64)
        // ==========================================
        if (cursor != null && !cursor.isEmpty()) {
            try {
                // Giải mã Base64 -> "ROOM#123_456||STATUS#ACTIVE#TIME#2026-04-12T14:18:19.904954Z"
                String rawCursor = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = rawCursor.split("\\|\\|");

                if (parts.length == 2) {
                    Map<String, AttributeValue> exclusiveStartKey = new HashMap<>();
                    // Base PK
                    exclusiveStartKey.put("pk", AttributeValue.builder().s(parts[0]).build());
                    // Base SK (chính là GSI PK)
                    exclusiveStartKey.put("sk", AttributeValue.builder().s(gsiPartitionKey).build());
                    // GSI SK (định dạng thời gian của bạn)
                    exclusiveStartKey.put("statusTime", AttributeValue.builder().s(parts[1]).build());

                    builder.exclusiveStartKey(exclusiveStartKey);
                }
            } catch (Exception e) {
                // Log lỗi nếu cursor không hợp lệ, DynamoDB sẽ bỏ qua và fetch trang đầu tiên
                System.err.println("Invalid cursor format: " + cursor);
            }
        }

        SdkIterable<Page<RoomMember>> results = index.query(builder.build());
        Page<RoomMember> firstPage = results.iterator().hasNext() ? results.iterator().next() : null;

        if (firstPage == null || firstPage.items().isEmpty()) {
            return PageResponse.<RoomResponse>builder().items(List.of()).build();
        }

        // ==========================================
        // 2. MÃ HÓA CURSOR CHO TRANG TIẾP THEO (Encode Base64)
        // ==========================================
        String nextCursor = null;
        if (firstPage.lastEvaluatedKey() != null && !firstPage.lastEvaluatedKey().isEmpty()) {
            String lastPk = firstPage.lastEvaluatedKey().get("pk").s();
            String lastStatusTime = firstPage.lastEvaluatedKey().get("statusTime").s();

            // Ghép lại: "ROOM#123_456||STATUS#ACTIVE#TIME#2026-04-12T14:18:19.904954Z"
            String rawNextCursor = lastPk + "||" + lastStatusTime;

            // Mã hóa thành Base64 để trả về Frontend
            nextCursor = Base64.getEncoder().encodeToString(rawNextCursor.getBytes(StandardCharsets.UTF_8));
        }

        // ==========================================
        // 3. MAP RESPONSE (Giữ nguyên logic của bạn)
        // ==========================================
        List<RoomResponse> roomResponses = firstPage.items().stream().map(i -> {
            Room room = roomService.getRoomById(i.getPk(), false);
            RoomResponse response = getRoomMetadata(userId, i.getPk());
            response.setLatestMessage(chatService.getLatestMessage(userId, Helper.normalizeId(i.getPk())));
//            RoomResponse.RoomResponseBuilder responseBuilder = RoomResponse.builder()
//                    .id(i.getPk())
//                    .latestMessage(chatService.getLatestMessage(userId, Helper.normalizeId(i.getPk())))
//                    .roomType(room.getRoomType())
//                    .createdAt(i.getCreatedAt());

            if (room.getRoomType() == RoomType.DM) {
                List<String> memberIds = roomService.getMembersByRoomId(Helper.normalizeId(i.getPk()));
                if (memberIds.size() <= 2) {
                    memberIds.stream()
                            .filter(id -> !id.equals(userId))
                            .findFirst()
                            .ifPresent(otherUserId -> {
                                UserResponse userResponse = userService.getUserProfile(otherUserId);
                                response.setRoomName(userResponse.getName());
                                response.setAvatarUrl(userResponse.getAvatarUrl());
                            });
                }
            }
//            else { // GROUP
//                List<String> memberIds = roomService.getMembersByRoomId(Helper.normalizeId(i.getPk()));
//                if (memberIds.size() > 2) {
//                    String groupName = memberIds.stream()
//                            .limit(3)
//                            .map(memberId -> userService.getUserProfile(memberId).getName())
//                            .collect(Collectors.joining(", "));
//                    responseBuilder.roomName(groupName);
//                } else {
//                    responseBuilder.roomName(i.getRoomName());
//                }
//            }
//            return responseBuilder.build();
            return response;
        }).toList();

        return PageResponse.<RoomResponse>builder()
                .items(roomResponses)
                .nextCursor(nextCursor)
                .build();
    }

    @Override
    public RoomResponse getRoomMetadata(String userId, String roomId) { // lấy tên phòng
        Room room = roomService.getRoomById(roomId, true);

        if (room == null) { // Fallback khi phòng chưa tồn tại
            String[] parts = roomId.split("_");

            if (parts.length != 2) {
                throw new AppException(ErrorCode.ROOM_INVALID);
            }

            // Lấy ID của người kia bằng cách loại trừ ID của chính mình
            String otherUserId = parts[0].equals(userId) ? parts[1] :
                    (parts[1].equals(userId) ? parts[0] : null);

            // Nếu user hiện tại không nằm trong chuỗi ID phòng
            if (otherUserId == null) {
                throw new AppException(ErrorCode.ROOM_INVALID);
            }

            UserResponse stranger = userService.getUserProfile(otherUserId);

            return RoomResponse.builder()
                    .id(roomId)
                    .roomName(stranger.getName())
                    .avatarUrl(stranger.getAvatarUrl())
                    .roomType(RoomType.DM)
                    .build();
        }

        int unreadCount = getUnreadCount(userId, roomId);
        if (room.getRoomType() == RoomType.DM) {
            List<String> memberIds = roomService.getMembersByRoomId(roomId);
            if (memberIds.size() <= 2) {
                memberIds.stream()
                        .filter(id -> !id.equals(userId))
                        .findFirst().ifPresent(otherUserId -> {
                            UserResponse other = userService.getUserProfile(otherUserId);
                            room.setRoomName(other.getName());
                            room.setAvatarUrl(other.getAvatarUrl());
                        });

            }
        } else { // GROUP
//            List<String> memberIds = roomService.getMembersByRoomId(Helper.normalizeId(i.getPk()));
//            if (memberIds.size() > 2) {
//                String groupName = memberIds.stream()
//                        .limit(3)
//                        .map(memberId -> userService.getUserProfile(memberId).getName())
//                        .collect(Collectors.joining(", "));
//                responseBuilder.roomName(groupName);
//            } else {
//                responseBuilder.roomName(i.getRoomName());
//            }
        }

        return RoomResponse.builder()
                .id(roomId)
                .roomName(room.getRoomName())
                .avatarUrl(room.getAvatarUrl())
                .roomType(room.getRoomType())
                .unreadMessages(unreadCount)
                .build();
    }

    @Override
    public int getUnreadCount(String userId, String roomId) {
        String cleanRoomId = Helper.normalizeId(roomId);
        String cleanUserId = Helper.normalizeId(userId);

        try {
            Key key = Key.builder()
                    .partitionValue("ROOM#" + cleanRoomId)
                    .sortValue(("MEMBER#" + cleanUserId))
                    .build();

            RoomMember member = roomMemberTable.getItem(key);

            return (member != null)? member.getUnreadMessages() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void upsertMemberInbox(String roomId, String memberId, InboxStatus status, String now) {
        String pk = "ROOM#" + roomId;
        String sk = "MEMBER#" + memberId;

        Key key = Key.builder()
                .partitionValue(pk)
                .sortValue(sk)
                .build();

        // 1. Kiểm tra xem record đã tồn tại chưa
        RoomMember member = roomMemberTable.getItem(key);

        if (member != null) {
            // Cập nhật
            member.setUpdatedAt(now);

            // Lưu ý: Không thay đổi status cũ trừ khi có logic duyệt tin nhắn chờ ở chỗ khác.
            // Chỉ tính toán lại chuỗi statusTime dựa trên status hiện tại của DB.
            if (member.getStatus() != null) {
                member.setStatusTime("STATUS#" + member.getStatus().name() + "#TIME#" + now);
            }

            roomMemberTable.updateItem(member);

        } else {
            // Tạo mới
            RoomMember newMember = RoomMember.builder()
                    .pk(pk)
                    .sk(sk)
                    .status(status) // Trạng thái này do hàm sendMessage quyết định (ACTIVE hoặc PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .statusTime("STATUS#" + status.name() + "#TIME#" + now)
                    .build();

            roomMemberTable.putItem(newMember);
        }
    }
  
    @Override
    public void updateMemberInbox(String roomId, String memberId, String now) {
        Key key = Key.builder()
                .partitionValue("ROOM#" + roomId)
                .sortValue("MEMBER#" + memberId)
                .build();

        RoomMember member = roomMemberTable.getItem(key);

        if (member != null) {
            member.setUpdatedAt(now);
//            member.setLastMessagePreview(lastMessagePreview);

            // tính toán lại để GSI tự động sắp xếp
            if (member.getStatus() != null) {
                member.setStatusTime("STATUS#" + member.getStatus() + "#TIME#" + now);
            }

            roomMemberTable.updateItem(member);
        }
    }

    @Override
    public RoomMember getMemberById(String memberId, String roomId) {
        Key key = Key.builder()
                .partitionValue("ROOM#" + roomId)
                .sortValue("MEMBER#" + memberId)
                .build();

        return roomMemberTable.getItem(key);
    }
}

