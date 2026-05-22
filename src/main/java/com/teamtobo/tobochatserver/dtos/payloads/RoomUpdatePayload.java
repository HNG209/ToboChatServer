package com.teamtobo.tobochatserver.dtos.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomUpdatePayload {
    String newRoomName;
    String newRoomAvatar;
    Boolean allowSendMessage;
    Boolean allowAddMember;
    Boolean allowUpdateMetadata;
    Boolean approveMember;
}
