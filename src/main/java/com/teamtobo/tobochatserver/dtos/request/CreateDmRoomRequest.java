package com.teamtobo.tobochatserver.dtos.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

/** Request body để tạo hoặc mở phòng chat trực tiếp (DM) với một người dùng khác. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateDmRoomRequest {
    /** ID của người dùng cần mở DM cùng */
    String peerId;
}
