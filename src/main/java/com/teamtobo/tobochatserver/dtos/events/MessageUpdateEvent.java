package com.teamtobo.tobochatserver.dtos.events;

import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.entities.enums.MessageUpdateType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MessageUpdateEvent {
    private String roomId;
    private String messageId;
    private String userId;
    private MessageUpdateType type;
}