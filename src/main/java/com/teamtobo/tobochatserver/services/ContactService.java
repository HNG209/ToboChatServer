package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.FriendRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.FriendResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.enums.FriendRequestType;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;

public interface ContactService {
    PageResponse<FriendResponse> getFriends(String userId, String roomId, String nextCursor, int limit);
    void sendFriendRequest(String userId, String otherId);
    void cancelFriendRequest(String userId, String otherId);
    FriendStatus getFriendStatus(String userId, String otherId);
    PageResponse<FriendRequestResponse> getFriendRequests(
            FriendRequestType type,
            String userId,
            String cursor,
            int limit
    );
}
