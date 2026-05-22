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
public class MemberPermissionsResponse {
    boolean canUpdateRoomSettings;
    boolean canAddMember;
    boolean canSendMessage;
    boolean canUpdateMetadata;
    boolean canDisbandGroup;
    boolean canApproveMember;
    boolean canUpdateMemberRole;
    boolean canRemoveMember;
    boolean canGetPendingRequests;
}
