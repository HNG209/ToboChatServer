package com.teamtobo.tobochatserver.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollSubmitRequest {
    private String question;
    private List<PollOptionDto> options;
    private boolean multipleChoice;
    private boolean allowAddOption;
    private String deadline;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PollOptionDto {
        private String id; // Sẽ null nếu đây là lựa chọn mới được thêm vào lúc edit
        private String text;
    }
}
