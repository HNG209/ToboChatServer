package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.BaseEntity;
import com.teamtobo.tobochatserver.entities.documents.Attachment;
import com.teamtobo.tobochatserver.entities.enums.AttachmentStatus;
import com.teamtobo.tobochatserver.entities.enums.EntityType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@DynamoDbBean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AttachmentItem extends BaseEntity {
    String attachmentId;
    String messageId;
    String senderId;
    Attachment detail;
    @Builder.Default
    AttachmentStatus status = AttachmentStatus.ACTIVE;
    @Builder.Default
    List<String> deletedByUserIds = new ArrayList<>();

    @Override
    public EntityType getEntityType() {
        return EntityType.ATTACHMENT;
    }
}