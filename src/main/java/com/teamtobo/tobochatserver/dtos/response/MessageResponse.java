package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.entities.enums.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageResponse {
    String id;
    String roomId;
    String content;
    UserResponse user;
    MessageType messageType;
    MessageStatus messageStatus;
    String createdAt;
    boolean isSelf; // nếu là message của chính mình
}
