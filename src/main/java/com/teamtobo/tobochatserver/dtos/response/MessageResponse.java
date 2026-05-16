package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.entities.documents.Attachment;
import com.teamtobo.tobochatserver.entities.enums.MessageType;
import com.teamtobo.tobochatserver.entities.enums.ReactionType;
import com.teamtobo.tobochatserver.entities.enums.SystemAction;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageResponse {
    String id;
    String tempId; // id trên FE generate tạm để optimistic update
    String roomId;
    String content;
    MessageResponse replyTo;
    UserResponse user;
    List<Attachment> attachments;
    MessageType messageType;
    MessageStatus messageStatus;
    Map<String, Integer> reactionsSummary;
    String createdAt;

    // Dành cho tin nhắn hệ thống
    Map<String, String> metadata;
    SystemAction action;
}
