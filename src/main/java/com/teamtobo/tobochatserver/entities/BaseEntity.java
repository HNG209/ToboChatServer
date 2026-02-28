package com.teamtobo.tobochatserver.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public abstract class BaseEntity { // Single Tale (Dùng chung cho tất cả Entity)

    // Partition Key: Khoá phân vùng
    private String pk;

    // Sort Key: Khoá sắp xếp
    private String sk;

    // Loại entity (USER, MSG...)
    private String entityType;

    @DynamoDbPartitionKey
    @DynamoDbSecondarySortKey(indexNames = "GSI_FriendRequest")
    @DynamoDbAttribute("pk")
    public String getPk() { return pk; }

    @DynamoDbSortKey
    @DynamoDbSecondaryPartitionKey(indexNames = "GSI_FriendRequest")
    @DynamoDbAttribute("sk")
    public String getSk() { return sk; }
}