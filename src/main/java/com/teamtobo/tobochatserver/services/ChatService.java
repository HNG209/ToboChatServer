package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.PresignedUrlResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.enums.ReactionType;

import java.util.List;

public interface ChatService {
    MessageResponse getRoomMessage(String userId, String roomId, String messageId);
    MessageResponse getMessage(String messageId, String roomId);
    void addReaction(String userId, String roomId, String messageId, ReactionType reactionType);
    Message getMessageById(String messageId, String roomId);
    PageResponse<MessageResponse> getMessages(String userId, String roomId, String cursor, int limit, String direction);
    MessageResponse getLatestMessage(String userId, String roomId);
    void revokeMessage(String userId, String roomId, String messageId);
    void forwardMessages(String userId, String fromRoomId, List<String> messageIds, List<String> toRoomIds);
    PresignedUrlResponse generateAttachmentPresignedUrl(String fileName, String roomId, String contentType);
    void deleteMessage(String messageId, String roomId, String userId);
}
