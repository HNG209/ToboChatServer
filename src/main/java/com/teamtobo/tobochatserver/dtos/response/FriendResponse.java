package com.teamtobo.tobochatserver.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendResponse {
    String id; // pk
    String name;
    String avatarUrl;
    String createdAt; // Ngày đồng ý kết bạn
    public String getId() {
        return Helper.normalizeId(this.id);
    }
}

