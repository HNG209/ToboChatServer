package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.entities.enums.RoomType;

public interface RoomService {
    void createRoom(RoomCreateRequest request, RoomType roomType);
}
