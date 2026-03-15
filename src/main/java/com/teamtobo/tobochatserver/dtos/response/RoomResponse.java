package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Response trả về cho 1 Room.
 *
 * Với DM:
 *   - id: roomId
 *   - type: "DM"
 *   - name: tên của người bạn (bạn bè trong cuộc trò chuyện)
 *   - avatarUrl: ảnh đại diện của người bạn
 *   - friendId: ID của người bạn
 *
 * Với GROUP:
 *   - id: roomId
 *   - type: "GROUP"
 *   - name: tên nhóm
 *   - avatarUrl: ảnh nhóm
 *   - friendId: null
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomResponse {
    String id;       // roomId (UUID, sem prefix)
    String type;     // RoomType: DM | GROUP
    String name;     // Với DM: tên bạn bè; Với GROUP: tên nhóm
    String avatarUrl;
    String friendId; // Chỉ có với DM: ID của người bạn (đã bỏ prefix "USER#")
    String createdAt;

    public String getId() {
        return id;
    }

    public String getFriendId() {
        return friendId != null && friendId.contains("#") ? Helper.normalizeId(friendId) : friendId;
    }
}
