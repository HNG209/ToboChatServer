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
public class GroupPendingRequest extends BaseEntity {

    String userId;
    String roomId;
    String requesterId; // người add (A thêm B)

    String roomName;

    @Override
    @DynamoDbPartitionKey
    public String getPk() {
        return super.getPk(); // ROOM#roomId
    }

    @Override
    @DynamoDbSortKey
    public String getSk() {
        return super.getSk(); // PENDING#userId
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.GROUP_PENDING_REQUEST;
    }
}