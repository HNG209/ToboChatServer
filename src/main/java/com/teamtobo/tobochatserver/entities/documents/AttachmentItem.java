package com.teamtobo.tobochatserver.entities.documents;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class AttachmentItem {

    private String roomId;
    private String sortKey;
    private String attachmentId; // Thêm định danh file độc lập (UUID)
    private String messageId;    // Thêm để map ngược về tin nhắn gốc
    private String senderId;
    private String createdAt;

    private Attachment detail;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("roomId")
    public String getRoomId() {
        return "ROOM#" + roomId;
    }

    public void setRoomId(String roomId) {
        if (roomId != null && roomId.startsWith("ROOM#")) {
            this.roomId = roomId.substring(5);
        } else {
            this.roomId = roomId;
        }
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("sortKey")
    public String getSortKey() {
        // Nếu lúc lấy từ DB ra, sortKey đã được gán bởi Setter thì giữ nguyên
        if (this.sortKey != null) {
            return this.sortKey;
        }

        String typeGroup = getFileTypeGroup(detail != null ? detail.getContentType() : null);
        // Thêm attachmentId (UUID) vào cuối để tránh trùng lặp bản ghi khi tạo mới
        return "ATTACHMENT#" + typeGroup + "#" + createdAt + "#" + attachmentId;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    @DynamoDbAttribute("attachmentId")
    public String getAttachmentId() { return attachmentId; }

    @DynamoDbAttribute("messageId")
    public String getMessageId() { return messageId; }

    @DynamoDbAttribute("senderId")
    public String getSenderId() { return senderId; }

    @DynamoDbAttribute("createdAt")
    public String getCreatedAt() { return createdAt; }

    @DynamoDbAttribute("detail")
    public Attachment getDetail() { return detail; }

    /**
     * Hàm phụ để phân loại nhóm file dựa vào ContentType
     */
    private String getFileTypeGroup(String contentType) {
        if (contentType == null) return "FILE";
        if (contentType.startsWith("image/") || contentType.startsWith("video/")) {
            return "MEDIA";
        }
        return "FILE";
    }
}