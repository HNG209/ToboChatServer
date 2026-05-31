package com.teamtobo.tobochatserver.dtos.events;

import com.teamtobo.tobochatserver.entities.enums.UserPresenceStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserPresenceUpdateEvent {
    String userId;
    UserPresenceStatus status;
}
