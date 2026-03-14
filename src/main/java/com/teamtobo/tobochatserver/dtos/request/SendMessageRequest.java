package com.teamtobo.tobochatserver.dtos.request;

import com.teamtobo.tobochatserver.entities.enums.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SendMessageRequest {
    String content;
    MessageType messageType;
}
