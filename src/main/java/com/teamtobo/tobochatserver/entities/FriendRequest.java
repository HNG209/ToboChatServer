package com.teamtobo.tobochatserver.entities;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
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
    String createdAt;

    String gsi1pk;
    String gsi1sk;

    @Override
    @DynamoDbPartitionKey
    public String getPk() { return super.getPk(); } // USER#Sender

    @Override
    @DynamoDbSortKey
    public String getSk() { return super.getSk(); } // REQUEST#Receiver

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI_FriendRequest")
    public String getGsi1pk() { return gsi1pk; } // REQUEST#Receiver

    @DynamoDbSecondarySortKey(indexNames = "GSI_FriendRequest")
    public String getGsi1sk() { return gsi1sk; } // USER#Sender
}