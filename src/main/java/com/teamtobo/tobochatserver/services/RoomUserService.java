package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.RoomResponse;

public interface RoomUserService {
    RoomResponse getRoomMetadata(String userId, String roomId);
}
