package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.entities.enums.RoomType;

import java.util.List;

public interface RoomService {
    void createRoom(RoomCreateRequest request, RoomType roomType);
    List<String> getMembersByRoomId(String roomId);
}
