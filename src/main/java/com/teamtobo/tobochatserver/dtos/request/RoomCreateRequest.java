package com.teamtobo.tobochatserver.dtos.request;

import com.teamtobo.tobochatserver.entities.enums.RoomType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomCreateRequest {
    String roomName;
    List<String> memberIds;
}
