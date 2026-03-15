package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    String id;           // Room ID (without "ROOM#" prefix)
    String type;         // "DM" | "GROUP"

    // For DM rooms: the friend's info
    String friendId;
    String friendName;
    String friendAvatarUrl;

    // For GROUP rooms: group info
    String name;
    String avatarUrl;

    String createdAt;
    String lastMessageAt;

    public String getId() {
        return Helper.normalizeId(this.id);
    }

    public String getFriendId() {
        return friendId != null ? Helper.normalizeId(this.friendId) : null;
    }
}
