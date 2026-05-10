package com.teamtobo.tobochatserver.dtos.events;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoomUpdateEvent {
    String roomId;
    String newRoomName;
    String newRoomAvatar;
}
