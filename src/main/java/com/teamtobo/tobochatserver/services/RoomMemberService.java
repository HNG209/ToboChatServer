package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;

import java.util.List;

public interface RoomMemberService {
    void upsertMemberInbox(String roomId, String memberId, InboxStatus status, String now); // v1
    void upsertMemberInbox(String roomId, String memberId, InboxStatus status, String now, MessageResponse message); // v2
    RoomMember getMemberById(String memberId, String roomId);
    RoomMemberResponse getMember(String memberId, String roomId);
    RoomMemberResponse getMyProfile(String userId, String roomId);
    MemberPermissionsResponse buildMemberPermission(RoomMemberResponse member, Room room);
    PageResponse<RoomResponse> getJoinedRooms(String userId, String cursor, int limit, InboxStatus status);
    RoomResponse getRoomMetadata(String userId, String roomId);
    void increaseUnreadCount(String senderId, String roomId);
    void markAsReadMessage(String userId, String roomId);
    int getUnreadCount(String userId, String roomId);
    List<RoomMember> findAllRoomMembers(String roomId);
    PageResponse<RoomMemberResponse> getRoomMembers(String roomId, String cursor, int limit);
}

