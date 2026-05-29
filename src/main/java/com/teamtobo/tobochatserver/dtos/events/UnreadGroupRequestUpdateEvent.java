package com.teamtobo.tobochatserver.dtos.events;

import com.teamtobo.tobochatserver.entities.enums.UnreadUpdateType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UnreadGroupRequestUpdateEvent {
    private String userId;
    UnreadUpdateType type;
}
