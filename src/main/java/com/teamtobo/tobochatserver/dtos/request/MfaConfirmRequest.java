package com.teamtobo.tobochatserver.dtos.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MfaConfirmRequest {
    private String otp;
}
