package com.teamtobo.tobochatserver.dtos.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PollCreateRequest {
    String question;
    List<String> options;

    @Builder.Default
    boolean multipleChoice = false; // Cho phép chọn nhiều phương án

    @Builder.Default
    boolean allowAddOption = false; // Cho phép thành viên khác thêm phương án mới

    String deadline;
}
