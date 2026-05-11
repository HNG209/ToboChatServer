package com.teamtobo.tobochatserver.dtos.events;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class WidgetMessageCreateEvent {
    private String roomId;
    private String senderId;
    private Map<String, String> metadata;
}
