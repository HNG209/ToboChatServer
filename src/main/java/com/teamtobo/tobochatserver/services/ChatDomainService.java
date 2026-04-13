package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;

public interface ChatDomainService {
    MessageResponse sendMessage(String senderId, String roomId, SendMessageRequest request);
}
