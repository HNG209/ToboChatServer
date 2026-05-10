package com.teamtobo.tobochatserver.dtos;

import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IcomingCallDto {
    String callerId;
    String token;
    RoomResponse room;
}
