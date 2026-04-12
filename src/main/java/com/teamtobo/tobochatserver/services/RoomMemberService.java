package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;

public interface RoomMemberService {
    void upsertMemberInbox(String roomId, String memberId, InboxStatus status, String now);
    void updateMemberInbox(String roomId, String memberId, String now);
    PageResponse<RoomResponse> getJoinedRooms(String userId, String cursor, int limit);
    PageResponse<RoomResponse> getJoinedRooms(String userId, String cursor, int limit, InboxStatus status);
    void increaseUnreadCount (String senderId, String roomId);
    void markAsReadedMessage (String userId, String roomId);
    int getUnreadCount (String userId, String roomId);
}

