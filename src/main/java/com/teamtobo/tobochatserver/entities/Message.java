package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.MessageType;
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
public class Message extends BaseEntity {
    String content;
    String senderId;
    MessageType messageType;
    @Override
    public String getEntityType() {
        return "MESSAGE";
    }
}
