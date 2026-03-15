package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.EntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public abstract class BaseEntity { // Single Tale (Dùng chung cho tất cả Entity)
    private String pk;
    private String sk;
    private EntityType entityType;

    @Builder.Default
    private String createdAt = Instant.now().toString();

    @Builder.Default
    private String updatedAt = Instant.now().toString();

    @DynamoDbPartitionKey
    @DynamoDbSecondarySortKey(indexNames = "GSI_FriendRequest")
    @DynamoDbAttribute("pk")
    public String getPk() { return pk; }

    @DynamoDbSortKey
    @DynamoDbSecondaryPartitionKey(indexNames = "GSI_FriendRequest")
    @DynamoDbAttribute("sk")
    public String getSk() { return sk; }
}