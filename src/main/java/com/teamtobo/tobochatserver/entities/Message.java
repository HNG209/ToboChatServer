package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.documents.Attachment;
import com.teamtobo.tobochatserver.entities.enums.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@DynamoDbBean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Message extends BaseEntity {
    String content;
    String senderId;
    String replyTo; // id của tin nhắn phản hồi
    MessageType messageType; // SYSTEM nếu là tin nhắn hệ thống
    MessageStatus messageStatus;
    List<Attachment> attachments;

    @Builder.Default
    Map<String, Integer> reactionsSummary = new HashMap<>();

    // Lưu dữ liệu động cho tin nhắn hệ thống
    // Ví dụ: {"targetUserId": "user_456", "newRoomName": "new room name 123"}
    Map<String, String> metadata;
    SystemAction action;

    @DynamoDbAttribute("reactionsSummary")
    public Map<String, Integer> getReactionsSummary() {
        return reactionsSummary;
    }

    public void setReactionsSummary(Map<String, Integer> reactionsSummary) {
        this.reactionsSummary = reactionsSummary;
    }

    @DynamoDbAttribute("metadata")
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    @DynamoDbAttribute("attachments")
    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    @Builder.Default
    List<String> deletedByUserIds = new ArrayList<>();
    @Override
    public EntityType getEntityType() {
        return EntityType.MESSAGE;
    }
}