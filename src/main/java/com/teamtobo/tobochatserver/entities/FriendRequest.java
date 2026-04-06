package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.EntityType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@DynamoDbBean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendRequest extends BaseEntity {
    String name;
    String avatarUrl;
    @Override
    public EntityType getEntityType() {
        return EntityType.FRIEND_REQUEST;
    }

    String gsi1pk;
    String gsi1sk;

    @Override
    @DynamoDbPartitionKey
    public String getPk() { return super.getPk(); } // USER#Sender

    @Override
    @DynamoDbSortKey
    public String getSk() { return super.getSk(); } // REQUEST#Receiver

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI_FriendRequest")
    public String getGsi1pk() { return super.getSk(); } // REQUEST#Receiver

    @DynamoDbSecondarySortKey(indexNames = "GSI_FriendRequest")
    public String getGsi1sk() { return super.getPk(); } // USER#Sender
}