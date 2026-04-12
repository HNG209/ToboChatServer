package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.EntityType;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.entities.enums.MessageType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@DynamoDbBean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Message extends BaseEntity {
    String content;
    String senderId;
    MessageType messageType;
    MessageStatus messageStatus;
    List<String> deletedBy;
    @Override
    public EntityType getEntityType() {
        return EntityType.MESSAGE;
    }
}
