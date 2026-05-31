package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamtobo.tobochatserver.entities.documents.LatestMessage;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.services.UserService;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomResponse {
    String id;
    String roomName;
    String avatarUrl;
    RoomType roomType;
    LatestMessage latestMessage;

    Boolean allowAddMember;
    Boolean allowSendMessage;
    Boolean allowUpdateMetadata;
    Boolean approveMember;

    Integer memberCount;
    Integer pendingCount;
    int unreadMessages;

    String createdAt;

    UserPresenceResponse userPresence;

    // Normalize id from pk
    public String getId() {
        return Helper.normalizeId(this.id);
    }
}
