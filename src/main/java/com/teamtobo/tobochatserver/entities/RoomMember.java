package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.EntityType;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
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
    MemberRole role;
    String roomName;
    String lastActivityAt;
    InboxStatus status;
    RoomType roomType;

    // GSI_ChatInbox
    String statusTime;

    //notification
    int unreadMessages;

    String addedBy; // userId của người thêm vào phòng, có thể null nếu là phòng private 1-1

    @Override
    @DynamoDbPartitionKey
    public String getPk() { return super.getPk(); }
    @Override
    @DynamoDbSortKey
    @DynamoDbSecondaryPartitionKey(indexNames = "GSI_ChatInbox")
    public String getSk() { return super.getSk(); }
    @DynamoDbSecondarySortKey(indexNames = "GSI_ChatInbox")
    public String getStatusTime() {
        return this.statusTime;
    }
    public String getMemberId() {
        return Helper.normalizeId(super.getSk());
    }
    @Override
    public EntityType getEntityType() {
        return EntityType.ROOM_MEMBER;
    }
}
