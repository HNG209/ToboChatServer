package com.teamtobo.tobochatserver.dtos.request;

import com.teamtobo.tobochatserver.entities.documents.Attachment;
import com.teamtobo.tobochatserver.entities.enums.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SendMessageRequest {
    String tempId;
    String content;
    String replyTo;
    List<Attachment> attachments;
}
