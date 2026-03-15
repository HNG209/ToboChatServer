package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.EntityType;
import com.teamtobo.tobochatserver.utils.Helper;
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
    String role;
    String roomName;
    String lastActivityAt;

    // GSI_RoomMember
    String memberPk;
    String memberSk;

    // Phase 1: upgrade later
    @Override
    @DynamoDbPartitionKey
    public String getPk() { return super.getPk(); }
    @Override
    @DynamoDbSortKey
    public String getSk() { return super.getSk(); }

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI_RoomMember")
    public String getMemberPk() { return super.getSk(); }
    @DynamoDbSecondarySortKey(indexNames = "GSI_RoomMember")
    public String getMemberSk() { return super.getPk(); }
    //====================

    public String getMemberId() {
        return Helper.normalizeId(super.getSk());
    }
    @Override
    public EntityType getEntityType() {
        return EntityType.ROOM_MEMBER;
    }
}
