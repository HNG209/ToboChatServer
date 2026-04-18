package com.teamtobo.tobochatserver.dtos.request;

import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MemberUpdateRequest {
    MemberRole memberRole;
}
