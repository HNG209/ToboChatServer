package com.teamtobo.tobochatserver.dtos.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NewRoomPayload {
    RoomResponse room;
    InboxStatus inboxStatus;
}
