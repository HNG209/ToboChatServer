package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.entities.enums.MessageType;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.entities.enums.SystemAction;

import java.util.Map;

public interface ChatDomainService {
    MessageResponse sendMessage(String senderId, String roomId, SendMessageRequest request);
    void sendSystemMessage(String roomId, String actorId, SystemAction action, Map<String, String> metadata);
}
