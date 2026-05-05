package com.teamtobo.tobochatserver.dtos.events;

import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InboxUpdateEvent {
    private String roomId;
    private String senderId;
    private MessageResponse message;
}
