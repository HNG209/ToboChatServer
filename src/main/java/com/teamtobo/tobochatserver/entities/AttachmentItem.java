package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.BaseEntity;
import com.teamtobo.tobochatserver.entities.documents.Attachment;
import com.teamtobo.tobochatserver.entities.enums.EntityType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

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

    @Override
    public EntityType getEntityType() {
        return EntityType.ATTACHMENT;
    }
}