package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.MemberUpdateRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.FriendResponse;
import com.teamtobo.tobochatserver.dtos.response.LeaveCheckResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.GroupAcceptRequest;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.RoomType;

import java.util.List;

public interface RoomDomainService {
    RoomResponse createRoom(String userId, RoomCreateRequest request, RoomType roomType);
    void updateRoomSettings(String roomId, RoomUpdateRequest request);
    List<FriendResponse> addMemberToGroup(String roomId, String inviterId, List<String> targetUserIds);
    String getOrCreateDMRoom(String userId, String otherId);
    void approveMember(String roomId, String adminId, String targetUserId, boolean accept);
    void updateMember(String roomId, String targetUserId, MemberUpdateRequest request);
    void removeMember(String roomId, String removerId, String memberId);
    void disbandGroup(String roomId);
//    PageResponse<GroupAcceptRequest> getSentInvites(String roomId, String cursor, int limit);
    LeaveCheckResponse checkLeave(String userId, String roomId);
    void leaveGroup(String userId, String roomId, String newAdminId);
    RoomMember getMember(String roomId, String userId);
    void updateRoomAvatar(String roomId, String avatarUrl);
    void updateRoomName(String roomId, String roomName);
}
