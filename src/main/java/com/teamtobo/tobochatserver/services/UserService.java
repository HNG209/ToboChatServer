package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.User;

import java.util.List;
import java.util.Map;

public interface UserService {
    User getUserById(String userId);
    Map<String, UserResponse> getUsersMapByIds(List<String> userIds); // batch get
    UserResponse getUserProfile(String userId);
    User updateUserProfile(String userId, UserUpdateRequest request);
    MfaInitResponse initEnableMFA(String userId, String password);
    void confirmEnableMFA(String userId, String otp);
    void disableMFA(String userId, String password);

    PresignedUploadResponse getAvatarUploadUrl(String fileName, String name);
    void increaseFriendRequestCount(String userId, String senderId);
    void markReadFriendRequest(String userId);
    void increaseGroupRequestCount(String userId, String senderId, String roomId);
    void markReadGroupRequest(String userId);

}
