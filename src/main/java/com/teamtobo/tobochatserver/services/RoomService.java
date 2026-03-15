package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.CreateRoomRequest;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;

public interface RoomService {
    RoomResponse createRoom(String userId, CreateRoomRequest request);
    PageResponse<RoomResponse> getRooms(String userId, String cursor, int limit);
    RoomResponse getRoom(String userId, String roomId);
}
