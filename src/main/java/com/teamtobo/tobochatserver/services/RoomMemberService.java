package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;

public interface RoomMemberService {
    PageResponse<RoomResponse> getJoinedRooms(String userId, String cursor, int limit);
}

