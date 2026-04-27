package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String id; // pk
    String name;
    String dob;
    String email;
    String avatarUrl;
    String createdAt;

    FriendStatus friendStatus;

    Integer totalUnreadMessages;
    Integer friendRequestCount;
    Integer groupRequestCount;

    Boolean allowAutoAddToGroup;
    public String getId() {
        return Helper.normalizeId(this.id);
    }
}
