package com.teamtobo.tobochatserver.dtos.events;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CallCancelledEvent {
    private String callerId;
    private String roomId;
}
