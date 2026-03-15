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
public class RoomMember extends BaseEntity {
    // roomType: "DM" | "GROUP"
    String roomType;

    // For DM rooms: the other user's info (denormalized for fast reads)
    String friendId;
    String friendName;
    String friendAvatarUrl;

    // GSI_RoomMembers: allows querying all members of a room
    // gsi1pk = ROOM#{roomId}, gsi1sk = USER#{userId}
    String gsi1pk;
    String gsi1sk;

    @Override
    public String getEntityType() {
        return "ROOM_MEMBER";
    }

    @Override
    @DynamoDbPartitionKey
    public String getPk() {
        return super.getPk(); // USER#{userId}
    }

    @Override
    @DynamoDbSortKey
    public String getSk() {
        return super.getSk(); // ROOM#{roomId}
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI_RoomMembers")
    public String getGsi1pk() {
        return gsi1pk; // ROOM#{roomId}
    }

    @DynamoDbSecondarySortKey(indexNames = "GSI_RoomMembers")
    public String getGsi1sk() {
        return gsi1sk; // USER#{userId}
    }
}
