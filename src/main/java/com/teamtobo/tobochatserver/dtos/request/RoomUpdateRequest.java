package com.teamtobo.tobochatserver.dtos.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomUpdateRequest {
    Boolean allowAddMember;
    Boolean allowSendMessage;
    Boolean allowUpdateMetadata;
    Boolean approveMember;
}
