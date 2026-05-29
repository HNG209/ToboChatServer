package com.teamtobo.tobochatserver.dtos.events;

import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoomCreateEvent {
    String userId;
    RoomCreateRequest request;
    RoomType roomType;
}
