package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.FriendResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;

public interface ContactService {
    PageResponse<FriendResponse> getFriends(String userId, String roomId, String nextCursor, int limit);
}
