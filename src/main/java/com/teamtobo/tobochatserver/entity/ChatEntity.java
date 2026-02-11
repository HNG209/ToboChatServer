package com.teamtobo.tobochatserver.entities;

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
@DynamoDbBean // Đánh dấu đây là Bean của DynamoDB
public class ChatEntity {

    private String pk;
    private String sk;
    private String entityType; // Loại: USER, MESSAGE, ROOM

    // Dữ liệu chung (Chat content, User name, Room name...)
    // Trong thực tế có thể dùng Map<String, Object> để linh động hơn
    private String data;

    private String senderId;
    private String timestamp;

    @DynamoDbPartitionKey // Map với cột 'pk' trong bảng
    @DynamoDbAttribute("pk")
    public String getPk() { return pk; }

    @DynamoDbSortKey // Map với cột 'sk' trong bảng
    @DynamoDbAttribute("sk")
    public String getSk() { return sk; }
}