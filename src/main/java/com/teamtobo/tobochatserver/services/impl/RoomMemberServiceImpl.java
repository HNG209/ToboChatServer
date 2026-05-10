package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.*;
import com.teamtobo.tobochatserver.services.handlers.ActiveRoomManager;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomMemberServiceImpl implements RoomMemberService {
    private final DynamoDbTable<RoomMember> roomMemberTable;
    private final RoomService roomService;
    private final UserService userService;
    private final ChatService chatService;
    private final DynamoDbClient dynamoDbClient;
    private final ActiveRoomManager activeRoomManager;
    private final SocketIOServer socketIOServer;

    @Override
    public PageResponse<RoomMemberResponse> getRoomMembers(String roomId, String cursor, int limit) {
        if (roomId == null || roomId.trim().isEmpty()) {
            return PageResponse.<RoomMemberResponse>builder().items(List.of()).build();
        }

        // 1. Xác định Partition Key và Sort Key Prefix cho Bảng chính
        String pkValue = "ROOM#" + roomId;
        String skPrefix = "MEMBER#";

        // 2. Xây dựng Query Builder với điều kiện sortBeginsWith
        QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.sortBeginsWith(
                        k -> k.partitionValue(pkValue).sortValue(skPrefix)
                ))
                .limit(limit);

        // 3. Xử lý phân trang (Pagination)
        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = new HashMap<>();
            // Truy vấn trên Base Table chỉ cần đúng pk và sk
            exclusiveStartKey.put("pk", AttributeValue.builder().s(pkValue).build());
            exclusiveStartKey.put("sk", AttributeValue.builder().s(cursor).build());

            builder.exclusiveStartKey(exclusiveStartKey);
        }

        // 4. Truy vấn trực tiếp trên Bảng chính (không dùng .index())
        SdkIterable<Page<RoomMember>> results = roomMemberTable.query(builder.build());
        Iterator<Page<RoomMember>> iterator = results.iterator();

        if (!iterator.hasNext()) {
            return PageResponse.<RoomMemberResponse>builder().items(List.of()).build();
        }

        Page<RoomMember> page = iterator.next();

        // 5. Lấy cursor cho trang tiếp theo (chính là giá trị SK của thành viên cuối cùng)
        String nextCursor = null;
        if (page.lastEvaluatedKey() != null && page.lastEvaluatedKey().containsKey("sk")) {
            nextCursor = page.lastEvaluatedKey().get("sk").s();
        }

        // 6. Map kết quả sang Response
        return PageResponse.<RoomMemberResponse>builder()
                .items(page.items().stream().map(
                        item -> RoomMemberResponse.builder()
                                // Helper.normalizeId sẽ cắt tiền tố "MEMBER#" đi để trả về ID sạch
                                .id(item.getMemberId())
                                .status(item.getStatus())
                                .role(item.getRole())
                                .roomType(item.getRoomType())
                                .addedBy(item.getAddedBy())
                                .member(userService.getUserProfile(item.getMemberId()))
                                .build()
                ).toList())
                .nextCursor(nextCursor)
                .build();
    }

    @Override
    public void increaseUnreadCount(String senderId, String roomId) {
        String cursor = null;
        do {
            PageResponse<RoomMemberResponse> pageResponse = getRoomMembers(roomId, cursor, 10);
            List<RoomMemberResponse> members = pageResponse.getItems();

            for (RoomMemberResponse member : members) {
                String cleanMemberId = Helper.normalizeId(member.getId());
                if (cleanMemberId.equals(senderId)) continue;

                // Nếu đang có trạng thái là PENDING thì không update unread (tin nhắn chờ)
                if (member.getStatus() == InboxStatus.PENDING) {
                    log.info("Ignore update unread for user {}", cleanMemberId);
                    continue;
                }

                // Nếu user đang trong phòng thì không tăng unread nữa
                if (activeRoomManager.isActive(cleanMemberId, roomId)) {
                    log.info("User {} is in room {}, ignore update unread", cleanMemberId, roomId);
                    continue;
                }

                socketIOServer.getRoomOperations(member.getId())
                        .sendEvent("unread_updated", roomId);

                increaseRoomUnreadMessage(cleanMemberId, roomId);
                updateTotalUnreadMessage(cleanMemberId, 1);
            }

            cursor = pageResponse.getNextCursor();
        } while (cursor != null);
    }

    // Mark as read
    // 1. Reset số unread trong phòng
    // 2. Trừ lượng unread đó ra khỏi user
    @Override
    public void markAsReadMessage(String userId, String roomId) {
        RoomMember member = getMemberById(userId, roomId);

        if (member != null && member.getUnreadMessages() > 0) {
            int countToReduce = member.getUnreadMessages();

            updateTotalUnreadMessage(userId, -countToReduce);
            resetRoomUnreadMessage(userId, roomId);
        }
    }

    // Cập nhật tổng tin nhắn chưa đọc (tổng các tin nhắn chưa đọc của từng phòng)
    private void updateTotalUnreadMessage(String userId, int inc) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName("ToboChatTable")
                .key(Map.of(
                        "pk", AttributeValue.builder().s("USER#" + userId).build(),
                        "sk", AttributeValue.builder().s("PROFILE").build()
                ))
                .updateExpression("SET totalUnreadMessages = if_not_exists(totalUnreadMessages, :zero) + :inc")
                .expressionAttributeValues(Map.of(
                        ":inc", AttributeValue.builder().n(String.valueOf(inc)).build(),
                        ":zero", AttributeValue.builder().n("0").build()
                ))
                .build());
    }

    // Tăng số tin nhắn chưa đọc cho phòng của user
    private void increaseRoomUnreadMessage(String userId, String roomId) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName("ToboChatTable")
                .key(Map.of(
                        "pk", AttributeValue.builder().s("ROOM#" + roomId).build(),
                        "sk", AttributeValue.builder().s("MEMBER#" + userId).build()
                ))
                .updateExpression("SET unreadMessages = if_not_exists(unreadMessages, :zero) + :inc")
                .expressionAttributeValues(Map.of(
                        ":inc", AttributeValue.builder().n(String.valueOf(1)).build(),
                        ":zero", AttributeValue.builder().n("0").build()
                ))
                .build());
    }

    // Reset số tin nhắn chưa đọc của 1 phòng
    private void resetRoomUnreadMessage(String userId, String roomId) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName("ToboChatTable")
                .key(Map.of(
                        "pk", AttributeValue.builder().s("ROOM#" + roomId).build(),
                        "sk", AttributeValue.builder().s("MEMBER#" + userId).build()
                ))
                .updateExpression("SET unreadMessages = :zero")
                .expressionAttributeValues(Map.of(
                        ":zero", AttributeValue.builder().n("0").build()
                ))
                .build());
    }

    // Danh sách inbox của người dùng
    // status = ACTIVE -> tin nhắn đã khi đã đồng ý kết bạn hoặc tin nhắn từ nhóm
    // status = PENDING -> tin nhắn chờ, do người lạ chưa kết bạn gửi
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

        String nextCursor = null;
        if (firstPage.lastEvaluatedKey() != null && !firstPage.lastEvaluatedKey().isEmpty()) {
            String lastPk = firstPage.lastEvaluatedKey().get("pk").s();
            String lastStatusTime = firstPage.lastEvaluatedKey().get("statusTime").s();

            // Ghép lại: "ROOM#123_456||STATUS#ACTIVE#TIME#2026-04-12T14:18:19.904954Z"
            String rawNextCursor = lastPk + "||" + lastStatusTime;

            // Mã hóa thành Base64 để trả về Frontend
            nextCursor = Base64.getEncoder().encodeToString(rawNextCursor.getBytes(StandardCharsets.UTF_8));
        }

        List<RoomResponse> roomResponses = firstPage.items().stream().map(i -> {
            Room room = roomService.getRoomById(i.getPk(), false);
            RoomResponse response = getRoomMetadata(userId, i.getPk());
            response.setLatestMessage(chatService.getLatestMessage(userId, Helper.normalizeId(i.getPk())));

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
        }

        return RoomResponse.builder()
                .id(roomId)
                .roomName(room.getRoomName())
                .avatarUrl(room.getAvatarUrl())
                .roomType(room.getRoomType())
                .allowUpdateMetadata(room.isAllowUpdateMetadata())
                .allowSendMessage(room.isAllowSendMessage())
                .allowAddMember(room.isAllowAddMember())
                .approveMember(room.isApproveMember())
                .memberCount(room.getMemberCount())
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
    public List<RoomMember> findAllRoomMembers(String roomId) {
        String pk = "ROOM#" + roomId;

        QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(pk)
                        .sortValue("MEMBER#")
                        .build()
        );

        return roomMemberTable.query(r -> r.queryConditional(queryConditional))
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    // Tạo hoặc cập nhật Inbox, chuyển sang cơ chế partial update
    // Nếu dùng update của enhanced client:
    // Thread A: +1 unread → 6
    // Thread B: update member → overwrite old snapshot (5)
    // statusTime(quan trọng) dùng để sắp xếp thứ tự các inbox
    // status = ACTIVE -> tin nhắn đã khi đã đồng ý kết bạn hoặc tin nhắn từ nhóm
    // status = PENDING -> tin nhắn chờ, do người lạ chưa kết bạn gửi
    @Override
    public void upsertMemberInbox(String roomId, String memberId, InboxStatus status, String now) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName("ToboChatTable")
                .key(Map.of(
                        "pk", AttributeValue.builder().s("ROOM#" + roomId).build(),
                        "sk", AttributeValue.builder().s("MEMBER#" + memberId).build()
                ))
                .updateExpression("""
                            SET updatedAt = :now,
                                #status = if_not_exists(#status, :status),
                                statusTime = :statusTime
                        """)
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":now", AttributeValue.builder().s(now).build(),
                        ":status", AttributeValue.builder().s(status.name()).build(),
                        ":statusTime", AttributeValue.builder()
                                .s("STATUS#" + status.name() + "#TIME#" + now)
                                .build()
                ))
                .build());
    }

//    @Override
//    public void upsertMemberInbox(String roomId, String memberId, InboxStatus status, String now) {
//        String pk = "ROOM#" + roomId;
//        String sk = "MEMBER#" + memberId;
//
//        Key key = Key.builder()
//                .partitionValue(pk)
//                .sortValue(sk)
//                .build();
//
//        // 1. Kiểm tra xem record đã tồn tại chưa
//        RoomMember member = roomMemberTable.getItem(key);
//
//        if (member != null) {
//            System.out.println("update:" + member);
//            // Cập nhật
//            member.setUpdatedAt(now);
//
//            // Lưu ý: Không thay đổi status cũ trừ khi có logic duyệt tin nhắn chờ ở chỗ khác.
//            // Chỉ tính toán lại chuỗi statusTime dựa trên status hiện tại của DB.
//            if (member.getStatus() != null) {
//                member.setStatusTime("STATUS#" + member.getStatus().name() + "#TIME#" + now);
//            }
//
//            roomMemberTable.updateItem(member);
//        } else {
//            // Tạo mới
//            RoomMember newMember = RoomMember.builder()
//                    .pk(pk)
//                    .sk(sk)
//                    .status(status) // Trạng thái này do hàm sendMessage quyết định (ACTIVE hoặc PENDING)
//                    .createdAt(now)
//                    .updatedAt(now)
//                    .statusTime("STATUS#" + status.name() + "#TIME#" + now)
//                    .build();
//
//            roomMemberTable.putItem(newMember);
//        }
//    }

    @Override
    public RoomMember getMemberById(String memberId, String roomId) {
        Key key = Key.builder()
                .partitionValue("ROOM#" + roomId)
                .sortValue("MEMBER#" + memberId)
                .build();

        return roomMemberTable.getItem(key);
    }

    @Override
    public RoomMemberResponse getMember(String memberId, String roomId) {
        RoomMember member = getMemberById(memberId, roomId);
        return RoomMemberResponse.builder()
                .id(memberId)
                .roomName(member.getRoomName())
                .role(member.getRole())
                .roomType(member.getRoomType())
                .build();
    }

    @Override
    public RoomMemberResponse getMyProfile(String userId, String roomId) {
        RoomMemberResponse member = getMember(userId, roomId);
        Room room = roomService.getRoomById(roomId, false);
        MemberPermissionsResponse permissions = new MemberPermissionsResponse(); // mặc định false cho các quyền

        // Nếu là admin thì cho phép update settings phòng và giải tán
        if (member.getRole() == MemberRole.ADMIN) {
            permissions.setCanUpdateRoomSettings(true);
            permissions.setCanDisbandGroup(true);
        }

        // Duyệt thành viên nếu là trưởng hoặc phó nhóm
        if (member.getRole() == MemberRole.ADMIN || member.getRole() == MemberRole.VICE_ADMIN)
            permissions.setCanApproveMember(true);

        // Nếu không phải là member hoặc phòng đã bật cho phép thêm thành viên
        if (member.getRole() != MemberRole.MEMBER || room.isAllowAddMember())
            permissions.setCanAddMember(true);

        // Nếu không phải là member hoặc phòng đã bật cho phép gửi tin nhắn
        if (member.getRole() != MemberRole.MEMBER || room.getRoomType() == RoomType.DM || room.isAllowSendMessage())
            permissions.setCanSendMessage(true);

        // Nếu không phải là member hoặc phòng đã bật cho phép sửa thông tin phòng
        if (member.getRole() != MemberRole.MEMBER || room.isAllowUpdateMetadata())
            permissions.setCanUpdateMetadata(true);

        member.setPermissions(permissions);
        return member;
    }
}

