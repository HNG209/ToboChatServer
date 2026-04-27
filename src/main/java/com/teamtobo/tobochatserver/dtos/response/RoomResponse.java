package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    MessageResponse latestMessage;

    boolean allowAddMember;
    boolean allowSendMessage;
    boolean allowUpdateMetadata;
    boolean approveMember;

    int memberCount;
    int pendingCount;
    int unreadMessages;

    String createdAt;

    // Normalize id from pk
    public String getId() {
        return Helper.normalizeId(this.id);
    }
}
