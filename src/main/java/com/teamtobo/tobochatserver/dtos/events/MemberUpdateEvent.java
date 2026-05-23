package com.teamtobo.tobochatserver.dtos.events;

import com.teamtobo.tobochatserver.entities.Room;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class MemberUpdateEvent {
    String roomId;
    Room room;
}
