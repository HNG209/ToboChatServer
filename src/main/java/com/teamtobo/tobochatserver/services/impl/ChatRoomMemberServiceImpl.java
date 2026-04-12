package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.services.*;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomMemberServiceImpl implements ChatRoomMemberService {
    private final DynamoDbTable<RoomMember> roomMemberTable;
    private final RoomMemberService roomMemberService;
    private final RoomService roomService;
    private final UserService userService;
    private final ChatService chatService;
    private final SocketIOServer socketIOServer;


    @Override
    public PageResponse<MessageResponse> getMessageAndMarkAsRead(String userId, String roomId, String cursor, int limit) {
        if (cursor == null || cursor.isEmpty()) {
            roomMemberService.markAsReadedMessage(userId, roomId);
        }
        return chatService.getMessages(userId, roomId, cursor, limit);
    }

    @Override
    public void sendMessageAndIncreaseUnread(String senderId, String roomId, SendMessageRequest request) {
        chatService.sendMessage(senderId, roomId, request);
        roomMemberService.increaseUnreadCount(senderId, roomId);

        List<String> memberIds = roomService.getMembersByRoomId(roomId);

        for (String memberId : memberIds) {
            if (!memberId.equals(senderId)) {
                int newTotal = userService.getUserProfile(memberId).getTotalUnreadMessages();
                socketIOServer.getRoomOperations(memberId).sendEvent("total_unread_update", Map.of(
                        "totalUnreadUpdate", newTotal
                ));
            }
        }
    }

    /**
     * Lấy danh sách phòng đã tham gia của user với pagination
     * @param userId
     * @param cursor
     * @param limit
     * @return
     */
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
                            Room room = roomService.getRoomById(i.getPk());
                            RoomResponse.RoomResponseBuilder responseBuilder = RoomResponse.builder()
                                    .id(i.getPk())
                                    // tin nhắn mới nhất để hiển thị lên chat inbox
                                    .latestMessage(chatService.getLatestMessage(userId, Helper.normalizeId(i.getPk())))
                                    .roomType(room.getRoomType())
                                    .unreadMessages(i.getUnreadMessages())
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
}