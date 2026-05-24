package com.teamtobo.tobochatserver.dtos.events;

import com.teamtobo.tobochatserver.entities.documents.Attachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentSaveEvent {
    private String roomId;
    private String messageId;
    private String senderId;
    private String createdAt;
    private List<Attachment> rawAttachments; // List chứa URL tạm từ client gửi lên
    private List<Attachment> finalAttachments; // List chứa URL chính thức đã map sẵn địa chỉ để lưu
    private boolean isForwarded; // Cờ đánh dấu nếu là tin nhắn chuyển tiếp
}