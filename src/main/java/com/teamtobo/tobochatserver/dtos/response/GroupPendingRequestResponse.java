package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupPendingRequestResponse {
    String roomId;
    String roomName;

    UserResponse user;
    UserResponse requester;
}