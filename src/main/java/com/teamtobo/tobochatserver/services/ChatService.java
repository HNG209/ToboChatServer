package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;

import java.util.List;

public interface ChatService {
    PageResponse<MessageResponse> getMessages(String userId, String roomId, String cursor, int limit);
    MessageResponse getLatestMessage(String userId, String roomId);
    void sendMessage(String senderId, String roomId, SendMessageRequest request);
    void revokeMessage(String userId, String roomId, String messageId);
    void forwardToMultipleRooms( String userId,
                                 String fromRoomId,
                                 List<String> toRoomIds,
                                 List<String> messageIds);
}
