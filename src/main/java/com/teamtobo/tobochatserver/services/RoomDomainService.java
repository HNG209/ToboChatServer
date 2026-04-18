package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.MemberUpdateRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomUpdateRequest;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.RoomType;

import java.util.List;

public interface RoomDomainService {
    void createRoom(String userId, RoomCreateRequest request, RoomType roomType);
    void updateRoomSettings(String roomId, RoomUpdateRequest request);
    void addMemberToGroup(String roomId, String inviterId, List<String> targetUserIds);
    String getOrCreateDMRoom(String userId, String otherId);
    void approveMember(String roomId, String adminId, String targetUserId, boolean accept);
    void updateMember(String roomId, String targetUserId, MemberUpdateRequest request);
    void removeMember(String roomId, String removerId, String memberId);
    void disbandGroup(String roomId);
    RoomMember getMember(String roomId, String userId);
}
