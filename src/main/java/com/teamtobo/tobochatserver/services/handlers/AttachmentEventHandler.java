package com.teamtobo.tobochatserver.services.handlers;

import com.teamtobo.tobochatserver.dtos.events.AttachmentSaveEvent;
import com.teamtobo.tobochatserver.entities.documents.Attachment;
import com.teamtobo.tobochatserver.entities.documents.AttachmentItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentEventHandler {

    private final DynamoDbTable<AttachmentItem> attachmentItemTable;
    private final S3Client s3Client;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Async
    @EventListener
    public void handleAttachmentSave(AttachmentSaveEvent event) {
        log.info("Bắt đầu xử lý bất đồng bộ lưu kho file cho phòng: {}", event.getRoomId());
        try {
            List<Attachment> rawList = event.getRawAttachments();
            List<Attachment> finalList = event.getFinalAttachments();

            for (int i = 0; i < finalList.size(); i++) {
                Attachment finalAtt = finalList.get(i);

                // 1. Nếu không phải tin nhắn chuyển tiếp, tiến hành copy file trên S3 ngầm
                if (!event.isForwarded() && i < rawList.size()) {
                    Attachment rawAtt = rawList.get(i);
                    try {
                        String sourceKey = extractKeyFromUrl(rawAtt.getFileUrl());
                        String destKey = extractKeyFromUrl(finalAtt.getFileUrl());

                        copyS3ObjectWithRetry(sourceKey, destKey, 3);
                    } catch (Exception e) {
                        log.error("Thất bại khi copy S3 ngầm cho file: {}, bỏ qua lưu dòng này", finalAtt.getFileName(), e);
                        continue; // File lỗi s3 thì bỏ qua không lưu vào kho quản lý file
                    }
                }

                // 2. Tạo bản ghi độc lập cho từng Attachment (Lưu 2 bản)
                String attachmentUuid = UUID.randomUUID().toString(); // Khóa chống trùng đè dữ liệu

                AttachmentItem item = AttachmentItem.builder()
                        .roomId(event.getRoomId())
                        .attachmentId(attachmentUuid)
                        .messageId(event.getMessageId())
                        .senderId(event.getSenderId())
                        .createdAt(event.getCreatedAt())
                        .detail(finalAtt)
                        .build();

                // Lưu xuống DynamoDB bảng mới
                attachmentItemTable.putItem(item);
            }
            log.info("Hoàn thành phân tách và lưu {} file thành công cho phòng {}", finalList.size(), event.getRoomId());
        } catch (Exception e) {
            log.error("Lỗi hệ thống khi xử lý AttachmentSaveEvent cho phòng {}", event.getRoomId(), e);
        }
    }

    private String extractKeyFromUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            String decodedUrl = java.net.URLDecoder.decode(url, "UTF-8");
            int index = decodedUrl.indexOf("attachments/");
            if (index == -1) {
                index = decodedUrl.indexOf("temp-drafts");
            }
            if (index == -1) return decodedUrl;

            String key = decodedUrl.substring(index);
            if (key.contains("?")) {
                key = key.split("\\?")[0];
            }
            return key;
        } catch (Exception e) {
            return url;
        }
    }

    private void copyS3ObjectWithRetry(String sourceKey, String destKey, int maxRetries) throws Exception {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                s3Client.copyObject(CopyObjectRequest.builder()
                        .sourceBucket(bucketName)
                        .sourceKey(sourceKey)
                        .destinationBucket(bucketName)
                        .destinationKey(destKey)
                        .build());
                return;
            } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
                attempts++;
                if (attempts >= maxRetries) throw e;
                log.warn("S3 Key chưa sẵn sàng, đang thử lại lần {}... Key: {}", attempts, sourceKey);
                Thread.sleep(500);
            }
        }
    }
}