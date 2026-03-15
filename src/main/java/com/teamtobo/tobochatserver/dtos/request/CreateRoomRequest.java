package com.teamtobo.tobochatserver.dtos.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateRoomRequest {
    String type;     // "DM" | "GROUP"
    String friendId; // For DM: the other user's ID
    String name;     // For GROUP: group name
}
