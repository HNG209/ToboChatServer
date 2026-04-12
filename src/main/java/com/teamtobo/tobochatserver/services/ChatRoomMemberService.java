package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;

public interface ChatRoomMemberService {
    PageResponse<MessageResponse> getMessageAndMarkAsRead(String userId, String roomId, String cursor, int limit, String direction);
    void sendMessageAndIncreaseUnread (String senderId, String roomId, SendMessageRequest request);
    PageResponse<RoomResponse> getJoinedRooms(String userId, String cursor, int limit);
}
