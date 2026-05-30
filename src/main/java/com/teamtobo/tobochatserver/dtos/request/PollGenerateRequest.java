package com.teamtobo.tobochatserver.dtos.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PollGenerateRequest {
    String prompt; // prompt của người dùng
    String fileUrl; // duy nhất 1 file nếu có
}
