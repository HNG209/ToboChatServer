package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.MfaInitResponse;
import com.teamtobo.tobochatserver.entities.UserEntity;

public interface UserService {
    UserEntity getUserProfile(String userId);
    UserEntity updateUserProfile(String userId, UserUpdateRequest request);
    void sendFriendRequest(String userId, String otherId);
    void cancelFriendRequest(String userId, String otherId);
    void responseFriendRequest(String userId, FriendAcceptRequest request);
    MfaInitResponse initEnableMFA(String userId, String password);
    void confirmEnableMFA(String userId, String otp);
    void disableMFA(String userId, String password);
}
