package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.MessageReaction;
import com.teamtobo.tobochatserver.entities.documents.LatestMessage;
import com.teamtobo.tobochatserver.entities.enums.ReactionType;

import java.util.List;
import java.util.Map;

public interface ChatService {
    MessageResponse getRoomMessage(String userId, String roomId, String messageId);
    MessageResponse getMessage(String messageId, String roomId);
    Map<String, Message> getMessagesMapByIds(List<String> messageIds, String roomId);
    void addReaction(String userId, String roomId, String messageId, ReactionType reactionType);
    Map<String, MessageReaction> getMyReactionsMapByIds(String userId, List<String> messageIds);
    PageResponse<MessageReactionResponse> getMessageReactions(String messageId, String roomId, String cursor, int limit);
    Message getMessageById(String messageId, String roomId);
    PageResponse<MessageResponse> getMessages(String userId, String roomId, String cursor, int limit, String direction);
    MessageResponse getLatestMessage(String userId, String roomId);
    MessageResponse getRoomLatestMessage(String roomId);
    LatestMessage buildLatestMessage(MessageResponse message);
    MessageResponse buildMessageResponse(Message message);
    void revokeMessage(String userId, String roomId, String messageId);
    void forwardMessages(String userId, String fromRoomId, List<String> messageIds, List<String> toRoomIds);
    PresignedUrlResponse generateAttachmentPresignedUrl(String fileName, String roomId, String contentType);
    void deleteMessage(String messageId, String roomId, String userId);
}
