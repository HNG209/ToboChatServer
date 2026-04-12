package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;

public interface ChatService {
    MessageResponse getRoomMessage(String userId, String roomId, String messageId);
    PageResponse<MessageResponse> getMessages(String userId, String roomId, String cursor, int limit, String direction);
    MessageResponse getLatestMessage(String userId, String roomId);
    void deleteMessage(String messageId, String roomId, String userId);
}
