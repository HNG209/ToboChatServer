package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.RoomType;
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

    // Chỉ dùng cho GROUP: tên nhóm, ảnh nhóm
    String name;
    String avatarUrl;
    String type; // RoomType: DM | GROUP

    @Override
    public String getEntityType() {
        return "ROOM";
    }

    @Override
    @DynamoDbPartitionKey
    public String getPk() {
        return super.getPk();
    }

    @Override
    @DynamoDbSortKey
    public String getSk() {
        return "METADATA";
    }
}
