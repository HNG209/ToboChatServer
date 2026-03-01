package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.FriendEntity;
import com.teamtobo.tobochatserver.entities.UserEntity;
import com.teamtobo.tobochatserver.entities.enums.FriendRequestType;

public interface UserService {
    UserEntity getUserProfile(String userId);

    UserEntity updateUserProfile(String userId, UserUpdateRequest request);

    void sendFriendRequest(String userId, String otherId);

    void cancelFriendRequest(String userId, String otherId);

    void responseFriendRequest(String userId, FriendAcceptRequest request);

    PageResponse<FriendEntity> getFriends(
            String userId,
            String cursor,
            int limit
    );

    PageResponse<FriendEntity> getFriendRequests(
            FriendRequestType type,
            String userId,
            String cursor,
            int limit
    );
}
