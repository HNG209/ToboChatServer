package com.teamtobo.tobochatserver.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollData {
    private String question;
    private boolean multipleChoice;
    private boolean allowAddOption;
    private String deadline;
    private List<PollOption> options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PollOption {
        private String id; // Sinh tự động (ví dụ: opt_1, opt_2)
        private String text; // Nội dung lựa chọn (Phở, Cơm...)

        @Builder.Default
        private List<String> votedUserIds = new ArrayList<>(); // Danh sách user đã vote
    }
}