package com.teamtobo.tobochatserver.dtos.events;

import com.teamtobo.tobochatserver.entities.enums.SystemAction;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class SystemMessageCreateEvent {
    private String roomId;
    private String actorId;
    private SystemAction action;
    private Map<String, String> metadata;
}
