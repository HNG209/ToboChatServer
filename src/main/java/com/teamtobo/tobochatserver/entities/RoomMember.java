package com.teamtobo.tobochatserver.entities;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Lưu thông tin tư cách thành viên của người dùng trong một Room.
 * PK = USER#{userId}
 * SK = ROOM#{roomId}
 *
 * Đối với Room loại DM, lưu thêm thông tin của người kia (peer)
 * để không cần truy vấn thêm khi lấy danh sách phòng.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@DynamoDbBean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomMember extends BaseEntity {

    /** Loại phòng: "DM" hoặc "GROUP" */
    String roomType;

    // --- Thông tin hiển thị cho phòng GROUP ---
    /** Tên phòng (chỉ dùng cho GROUP) */
    String roomName;
    /** Ảnh đại diện phòng (chỉ dùng cho GROUP) */
    String roomAvatarUrl;

    // --- Thông tin người kia trong phòng DM ---
    /** ID của người kia trong cuộc trò chuyện DM */
    String peerId;
    /** Tên hiển thị của người kia (DM) */
    String peerName;
    /** Ảnh đại diện của người kia (DM) */
    String peerAvatarUrl;

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
}
