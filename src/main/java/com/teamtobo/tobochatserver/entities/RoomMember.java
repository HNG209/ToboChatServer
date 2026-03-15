package com.teamtobo.tobochatserver.entities;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Lưu thông tin thành viên trong 1 room (per user).
 *
 * Key design:
 *   PK = "USER#{userId}"
 *   SK = "ROOM_DM#{otherUserId}"  (với DM)
 *   SK = "ROOM_GROUP#{roomId}"    (với GROUP)
 *
 * Việc mã hoá otherUserId vào SK của DM cho phép kiểm tra phòng đã tồn tại
 * mà không cần scan (getItem bằng PK + SK trực tiếp).
 *
 * Đối với DM: name và avatarUrl là thông tin của người bạn (người còn lại),
 *   không phải tên room. Điều này giúp hiển thị thông tin bạn bè ngay mà
 *   không cần query thêm.
 *
 * Đối với GROUP: name và avatarUrl là tên và ảnh nhóm.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@DynamoDbBean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomMember extends BaseEntity {

    String type;    // RoomType: DM | GROUP

    // ID thực của Room (UUID, trùng với PK của Room entity sau khi bỏ prefix "ROOM#")
    // Dùng để client gửi message hoặc query lịch sử chat
    String roomId;

    // Với DM: ID của người bạn (dạng "USER#{friendId}")
    // Với GROUP: null
    String friendId;

    // Với DM: tên của người bạn (để hiển thị tên room phía client)
    // Với GROUP: tên của nhóm
    String name;

    // Với DM: ảnh đại diện của người bạn
    // Với GROUP: ảnh đại diện nhóm
    String avatarUrl;

    @Override
    public String getEntityType() {
        return "ROOM_MEMBER";
    }
}
