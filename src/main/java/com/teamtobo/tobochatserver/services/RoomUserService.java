package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.RoomResponse;

// Tránh lỗi circular dependencies, RoomService -> RoomUserService <- UserService
public interface RoomUserService {
    RoomResponse getRoomMetadata(String userId, String roomId);
}
