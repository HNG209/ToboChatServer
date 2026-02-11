package com.teamtobo.tobochatserver.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class ChatEntity { // Single Tale (Dùng chung cho tất cả Entity)

    // Partition Key: Khoá phân vùng
    private String pk;

    // Sort Key: Khoá sắp xếp
    private String sk;

    // Loại entity (USER, MSG...)
    private String entityType;

    private String data;

    private String senderId;
    private String timestamp;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("pk")
    public String getPk() { return pk; }

    @DynamoDbSortKey
    @DynamoDbAttribute("sk")
    public String getSk() { return sk; }
}