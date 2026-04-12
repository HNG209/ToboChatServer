package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.UserService;
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
    public int getUnreadCount (String userId, String roomId) {
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
}

