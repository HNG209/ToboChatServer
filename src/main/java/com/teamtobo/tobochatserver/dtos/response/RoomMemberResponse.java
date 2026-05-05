package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomMemberResponse {
    String id;
    String roomId;
    MemberRole role;
    String roomName;
    RoomType roomType;
    String addedBy;

    // Thông tin cá nhân (name, avatar)
    UserResponse member;

    // Permission, chỉ trả về khi lấy thông tin của chính tôi trong phòng
    MemberPermissionsResponse permissions;
}
