package com.teamtobo.tobochatserver.dtos.events;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ForwardMessageEvent {
    private String userId;
    private String fromRoomId;
    private List<String> roomIds;
    private List<String> messageIds;
}
