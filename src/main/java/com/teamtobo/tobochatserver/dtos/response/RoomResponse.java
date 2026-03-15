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
public class RoomResponse {
    String id;             // roomId

    // Loại phòng: "DM" hoặc "GROUP"
    String type;

    // Với DM: hiển thị tên + avatar của người bạn đang chat cùng
    // Với GROUP: hiển thị tên + avatar của nhóm
    String name;
    String avatarUrl;

    // Chỉ có ở DM: ID của người kia (để FE mở chat đúng người)
    String participantId;

    // Tin nhắn gần nhất
    String lastMessage;
    String lastMessageAt;
}
