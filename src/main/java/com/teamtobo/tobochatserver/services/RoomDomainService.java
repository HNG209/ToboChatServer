package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.entities.enums.RoomType;

import java.util.List;

public interface RoomDomainService {
    void createRoom(String userId, RoomCreateRequest request, RoomType roomType);
    void addMemberToGroup(String roomId, String inviterId, List<String> targetUserIds);
    String getOrCreateDMRoom(String userId, String otherId);
    void approveMember(String roomId, String adminId, String targetUserId, boolean accept);
    void toggleApproveMember(String roomId, String userId);
    void toggleAllowAddMember(String roomId, String userId);
    void toggleAllowSendMessage(String roomId, String userId);
    void toggleAllowUpdateGroup(String roomId, String userId);
    void addViceAdmin(String roomId, String adminId, String targetUserId);
    void removeMember(String roomId, String removerId, String targetUserId);
    void leaveGroup(String roomId, String userId);
}
