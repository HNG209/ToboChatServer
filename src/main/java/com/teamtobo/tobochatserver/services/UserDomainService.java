package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;

public interface UserDomainService {
    void responseFriendRequest(String userId, FriendAcceptRequest request);
}
