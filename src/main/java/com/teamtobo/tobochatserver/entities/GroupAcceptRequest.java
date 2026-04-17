package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.EntityType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@DynamoDbBean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupAcceptRequest extends BaseEntity {

    String roomId;
    String inviterId;
    String roomName;

    @Override
    public EntityType getEntityType() {
        return EntityType.GROUP_ACCEPT_REQUEST;
    }

    @Override
    @DynamoDbPartitionKey
    public String getPk() {
        return super.getPk();
    }

    @Override
    @DynamoDbSortKey
    public String getSk() {
        return super.getSk();
    }
}







