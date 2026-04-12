package com.teamtobo.tobochatserver.dtos.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ForwardRequest {
    String fromRoomId;
    List<String> toRoomIds;
    List<String> messageIds;
}
