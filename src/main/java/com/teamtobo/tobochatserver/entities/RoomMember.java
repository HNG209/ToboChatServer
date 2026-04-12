package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.EntityType;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
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
    InboxStatus status;

    // GSI_ChatInbox
    String statusTime;

    // GSI_RoomMember
    String roomPk;
    String roomSk;

    // Phase 1: upgrade later
    @Override
    @DynamoDbPartitionKey
    public String getPk() { return super.getPk(); }
    @Override
    @DynamoDbSortKey
    public String getSk() { return super.getSk(); }

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI_RoomMember")
    public String getRoomPk() { return super.getSk(); }
    @DynamoDbSecondarySortKey(indexNames = "GSI_RoomMember")
    public String getRoomSk() { return super.getPk(); }
    //====================

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI_ChatInbox")
    public String getUserSk() { return super.getSk(); }
    @DynamoDbSecondarySortKey(indexNames = "GSI_ChatInbox")
    public String getStatusTime() {
        return "STATUS#" + status + "#TIME#" + super.getUpdatedAt();
    }

    public String getMemberId() {
        return Helper.normalizeId(super.getSk());
    }
    @Override
    public EntityType getEntityType() {
        return EntityType.ROOM_MEMBER;
    }
}
