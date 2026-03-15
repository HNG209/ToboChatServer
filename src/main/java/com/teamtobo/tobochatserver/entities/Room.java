package com.teamtobo.tobochatserver.entities;

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
public class Room extends BaseEntity {
    String type;         // "DM" | "GROUP"
    String name;         // GROUP only
    String avatarUrl;    // GROUP only
    String lastMessageAt;

    @Override
    public String getEntityType() {
        return "ROOM";
    }

    @Override
    @DynamoDbPartitionKey
    public String getPk() {
        return super.getPk(); // ROOM#{roomId}
    }

    @Override
    @DynamoDbSortKey
    public String getSk() {
        return "METADATA";
    }
}
