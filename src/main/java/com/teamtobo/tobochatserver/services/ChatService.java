package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;

public interface ChatService {
    void sendMessage(String senderId, String roomId, SendMessageRequest request);
}
