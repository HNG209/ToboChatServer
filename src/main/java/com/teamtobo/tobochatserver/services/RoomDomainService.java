package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.entities.enums.RoomType;

public interface RoomDomainService {
    void createRoom(String userId, RoomCreateRequest request, RoomType roomType);
}
