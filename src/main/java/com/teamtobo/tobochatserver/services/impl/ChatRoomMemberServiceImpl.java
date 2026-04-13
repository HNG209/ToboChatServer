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
    private final ChatDomainService chatDomainService;

    @Override
    public PageResponse<MessageResponse> getMessageAndMarkAsRead(String userId, String roomId, String cursor, int limit, String direction) {
        if (cursor == null || cursor.isEmpty()) {
            roomMemberService.markAsReadedMessage(userId, roomId);
        }
        return chatService.getMessages(userId, roomId, cursor, limit, direction);
    }

    // TODO: Bỏ hàm này, loại bớt sự phụ thuộc
    @Override
    public MessageResponse sendMessageAndIncreaseUnread(String senderId, String roomId, SendMessageRequest request) {
        MessageResponse result = chatDomainService.sendMessage(senderId, roomId, request);
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

        return result;
    }
}