package com.teamtobo.tobochatserver.dtos.response;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupAcceptRequestResponse {
    String roomId;
    String roomName;
    String inviterId;
}