package com.teamtobo.tobochatserver.dtos.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CallResponse {
    String token;
    String roomId;
}
