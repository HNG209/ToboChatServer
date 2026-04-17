package com.teamtobo.tobochatserver.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupPendingRequestResponse {

    private String roomId;

    private String roomName;

    private String userId;       // người sẽ được add (B)

    private String requesterId;  // người request add (A)
}