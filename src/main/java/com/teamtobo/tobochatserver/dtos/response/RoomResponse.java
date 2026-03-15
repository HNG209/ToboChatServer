package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Response cho một Room.
 * - Nếu type = DM: trả về thông tin bạn bè qua trường {@code peer}.
 * - Nếu type = GROUP: trả về {@code name} và {@code avatarUrl} của phòng.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomResponse {

    String id;      // roomId (sau khi normalize, bỏ ROOM# prefix)
    String type;    // "DM" hoặc "GROUP"

    // Chỉ có giá trị với type = GROUP
    String name;
    String avatarUrl;

    // Chỉ có giá trị với type = DM: thông tin của người bạn trong phòng
    PeerInfo peer;

    public String getId() {
        return id;
    }

    /** Thông tin hiển thị của người bạn trong phòng DM */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PeerInfo {
        String id;
        String name;
        String avatarUrl;

        public String getId() {
            if (id == null) return null;
            return id.startsWith("USER#") ? Helper.normalizeId(id) : id;
        }
    }
}
