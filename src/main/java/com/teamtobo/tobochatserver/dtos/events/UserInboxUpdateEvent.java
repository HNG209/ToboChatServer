package com.teamtobo.tobochatserver.dtos.events;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInboxUpdateEvent {
    String userId;
    String roomId;
}
