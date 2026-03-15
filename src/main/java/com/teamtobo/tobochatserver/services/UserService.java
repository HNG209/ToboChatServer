package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.Friend;
import com.teamtobo.tobochatserver.entities.FriendRequest;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.entities.enums.FriendRequestType;

public interface UserService {
    UserResponse getUserProfile(String userId);

    User updateUserProfile(String userId, UserUpdateRequest request);

    void sendFriendRequest(String userId, String otherId);

    void cancelFriendRequest(String userId, String otherId);

    void responseFriendRequest(String userId, FriendAcceptRequest request);
    PageResponse<UserResponse> findByEmail(String userId, String email, String cursor, int limit);

    PageResponse<FriendResponse> getFriends(
            String userId,
            String cursor,
            int limit
    );

    PageResponse<FriendRequestResponse> getFriendRequests(
            FriendRequestType type,
            String userId,
            String cursor,
            int limit
    );

    MfaInitResponse initEnableMFA(String userId, String password);

    void confirmEnableMFA(String userId, String otp);

    void disableMFA(String userId, String password);

    PageResponse<RoomResponse> getRooms(String userId, String cursor, int limit);
}
