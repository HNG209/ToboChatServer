package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;

public interface RoomMemberService {
    void increaseUnreadCount (String senderId, String roomId);
    void markAsReadedMessage (String userId, String roomId);
    int getUnreadCount (String userId, String roomId);
}

