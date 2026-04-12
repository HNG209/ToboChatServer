package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.services.ChatService;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.UserService;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
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
    @Override
    public PageResponse<RoomResponse> getJoinedRooms(String userId, String cursor, int limit) {
        String gsiPartitionKey = "MEMBER#" + userId;
        DynamoDbIndex<RoomMember> index = roomMemberTable.index("GSI_RoomMember");

        QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(gsiPartitionKey)))
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = new HashMap<>();
            exclusiveStartKey.put("roomPk", AttributeValue.builder().s(gsiPartitionKey).build());
            exclusiveStartKey.put("roomSk", AttributeValue.builder().s(cursor).build());
            exclusiveStartKey.put("pk", AttributeValue.builder().s("ROOM#" + cursor.replace("ROOM#", "")).build());
            exclusiveStartKey.put("sk", AttributeValue.builder().s(gsiPartitionKey).build());

            builder.exclusiveStartKey(exclusiveStartKey);
        }

        SdkIterable<Page<RoomMember>> results = index.query(builder.build());
        Page<RoomMember> firstPage = results.iterator().next();

        if (firstPage == null || firstPage.items().isEmpty()) {
            return PageResponse.<RoomResponse>builder().items(List.of()).build();
        }

        String nextCursor = null;
        if (firstPage.lastEvaluatedKey() != null) {
            nextCursor = firstPage.lastEvaluatedKey().get("roomSk").s();
        }

        return PageResponse.<RoomResponse>builder()
                .items(firstPage.items().stream().map(
                        i -> {
                            // Lấy metadata của phòng để lấy thông tin roomType
                            Room room = roomService.getRoomById(i.getPk(), false);
                            RoomResponse.RoomResponseBuilder responseBuilder = RoomResponse.builder()
                                    .id(i.getPk())
                                    // tin nhắn mới nhất để hiển thị lên chat inbox
                                    .latestMessage(chatService.getLatestMessage(userId, Helper.normalizeId(i.getPk())))
                                    .roomType(room.getRoomType())
                                    .createdAt(i.getCreatedAt());
                            if (room.getRoomType() == RoomType.DM) {
                                List<String> memberIds = roomService.getMembersByRoomId(Helper.normalizeId(i.getPk()));
                                if (memberIds.size() <= 2) {
                                    memberIds.stream()
                                            .filter(id -> !id.equals(userId))
                                            .findFirst().ifPresent(otherUserId -> responseBuilder.roomName(userService.getUserProfile(otherUserId).getName()));

                                }
                            } else { // GROUP
                                List<String> memberIds = roomService.getMembersByRoomId(Helper.normalizeId(i.getPk()));
                                if (memberIds.size() > 2) {
                                    String groupName = memberIds.stream()
                                            .limit(3)
                                            .map(memberId -> userService.getUserProfile(memberId).getName())
                                            .collect(Collectors.joining(", "));
                                    responseBuilder.roomName(groupName);
                                } else {
                                    responseBuilder.roomName(i.getRoomName());
                                }
                            }

                            return responseBuilder.build();
                        }
                ).toList())
                .nextCursor(nextCursor)
                .build();
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
            RoomResponse.RoomResponseBuilder responseBuilder = RoomResponse.builder()
                    .id(i.getPk())
                    .latestMessage(chatService.getLatestMessage(userId, Helper.normalizeId(i.getPk())))
                    .roomType(room.getRoomType())
                    .createdAt(i.getCreatedAt());

            if (room.getRoomType() == RoomType.DM) {
                List<String> memberIds = roomService.getMembersByRoomId(Helper.normalizeId(i.getPk()));
                if (memberIds.size() <= 2) {
                    memberIds.stream()
                            .filter(id -> !id.equals(userId))
                            .findFirst()
                            .ifPresent(otherUserId -> responseBuilder.roomName(userService.getUserProfile(otherUserId).getName()));
                }
            } else { // GROUP
                List<String> memberIds = roomService.getMembersByRoomId(Helper.normalizeId(i.getPk()));
                if (memberIds.size() > 2) {
                    String groupName = memberIds.stream()
                            .limit(3)
                            .map(memberId -> userService.getUserProfile(memberId).getName())
                            .collect(Collectors.joining(", "));
                    responseBuilder.roomName(groupName);
                } else {
                    responseBuilder.roomName(i.getRoomName());
                }
            }
            return responseBuilder.build();
        }).toList();

        return PageResponse.<RoomResponse>builder()
                .items(roomResponses)
                .nextCursor(nextCursor)
                .build();
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
}

