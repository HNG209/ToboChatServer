package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.MemberUpdateRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.GroupAcceptRequest;
import com.teamtobo.tobochatserver.dtos.response.FriendResponse;
import com.teamtobo.tobochatserver.dtos.response.LeaveCheckResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.MemberStatus;
import com.teamtobo.tobochatserver.entities.enums.RoomType;

import java.util.List;

public interface RoomDomainService {
    RoomResponse createRoom(String userId, RoomCreateRequest request, RoomType roomType);
    void updateRoomSettings(String roomId, RoomUpdateRequest request);
    List<FriendResponse> addMemberToGroup(String roomId, String inviterId, List<String> targetUserIds);
    String getOrCreateDMRoom(String userId, String otherId);
    void approveMember(String roomId, String adminId, String targetUserId, boolean accept);
    void updateMemberRole(String roomId, String userId, String targetUserId, MemberUpdateRequest request);
    void removeMember(String roomId, String removerId, String memberId);
    void disbandGroup(String roomId);
    LeaveCheckResponse checkLeave(String userId, String roomId);
    void leaveGroup(String userId, String roomId, String newAdminId);
    RoomMember getMember(String roomId, String userId);
    void updateRoomAvatar(String userId, String roomId, String avatarUrl);
    void updateRoomName(String userId, String roomId, String roomName);


    // -----Neo4j services-----
    void addMemberNeo4j(String roomId, String userId);
    PageResponse<GroupAcceptRequestResponse> getAcceptRequests(String userId, String cursor, int limit);
    PageResponse<GroupPendingRequestResponse> getPendingRequests(String roomId, String userId, String cursor, int limit);
    void createGroupAcceptRequestNeo4j(String roomId, String inviterId, String targetUserId);
    void createGroupPendingRequestNeo4j(String roomId, String inviterId, String targetUserId);
    void respondInviteNeo4j(String userId, String roomId, boolean accepted);
    PageResponse<String> getJoinedRoomIdsNeo4j(String userId, String cursor, int limit);
    MemberStatus getMemberStatusNeo4j(String roomId, String userId);
    void deleteMemberRelationshipNeo4j(String roomId, String userId);
}
