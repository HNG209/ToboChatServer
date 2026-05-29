package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.entities.enums.FriendRequestType;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;

import java.util.List;
import java.util.Map;

public interface UserService {
    User getUserById(String userId);
    Map<String, UserResponse> getUsersMapByIds(List<String> userIds); // batch get
    UserResponse getUserProfile(String userId);
    User updateUserProfile(String userId, UserUpdateRequest request);
    void sendFriendRequest(String userId, String otherId);
    void cancelFriendRequest(String userId, String otherId);
    PageResponse<UserResponse> findByEmail(String userId, String email, String cursor, int limit);
    PageResponse<FriendResponse> getFriends(
            String userId,
            String roomId,
            String cursor,
            int limit
    );
    FriendStatus getFriendStatus(String userId, String otherId);
    PageResponse<FriendRequestResponse> getFriendRequests(
            FriendRequestType type,
            String userId,
            String cursor,
            int limit
    );
    MfaInitResponse initEnableMFA(String userId, String password);
    void confirmEnableMFA(String userId, String otp);
    void disableMFA(String userId, String password);

    PresignedUploadResponse getAvatarUploadUrl(String fileName, String name);
    void increaseFriendRequestCount(String userId);
    void markReadFriendRequest(String userId);
    void increaseGroupRequestCount(String userId);
    void markReadGroupRequest(String userId);

}
