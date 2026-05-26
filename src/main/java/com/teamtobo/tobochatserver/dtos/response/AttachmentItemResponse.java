package com.teamtobo.tobochatserver.dtos.response;

import com.teamtobo.tobochatserver.entities.documents.Attachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentItemResponse {
    private String attachmentId;
    private String messageId;
    private String senderId;
    private Attachment detail;
}