package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.AttachmentItemResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.AttachmentItem;
import com.teamtobo.tobochatserver.entities.enums.AttachmentStatus;
import com.teamtobo.tobochatserver.services.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    private final DynamoDbTable<AttachmentItem> attachmentItemTable;

    @Override
    public PageResponse<AttachmentItemResponse> getRoomAttachments(String userId, String roomId, String type, int limit, String cursor) {
        try {
            String partitionValue = "ROOM#" + roomId;
            String sortKeyPrefix = "ATTACHMENT#" + type + "#";

            QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                    Key.builder()
                            .partitionValue(partitionValue)
                            .sortValue(sortKeyPrefix)
                            .build()
            );

            QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .limit(limit)
                    .scanIndexForward(false);

            if (cursor != null && !cursor.isEmpty()) {
                Map<String, AttributeValue> exclusiveStartKey = new HashMap<>();
                exclusiveStartKey.put("pk", AttributeValue.builder().s(partitionValue).build());
                exclusiveStartKey.put("sk", AttributeValue.builder().s(cursor).build());

                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }

            Page<AttachmentItem> page = attachmentItemTable.query(requestBuilder.build())
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (page == null || page.items().isEmpty()) {
                return PageResponse.<AttachmentItemResponse>builder().items(List.of()).nextCursor(null).build();
            }

            List<AttachmentItemResponse> items = page.items().stream()
                    .filter(item -> item.getStatus() == null || item.getStatus() == AttachmentStatus.ACTIVE)
                    .filter(item -> item.getDeletedByUserIds() == null || !item.getDeletedByUserIds().contains(userId))
                    .map(item -> AttachmentItemResponse.builder()
                            .attachmentId(item.getAttachmentId())
                            .messageId(item.getMessageId())
                            .senderId(item.getSenderId())
                            .detail(item.getDetail())
                            .build())
                    .collect(Collectors.toList());

            String nextCursor = null;
            if (page.lastEvaluatedKey() != null && !page.lastEvaluatedKey().isEmpty()) {
                if (page.lastEvaluatedKey().containsKey("sk")) {
                    nextCursor = page.lastEvaluatedKey().get("sk").s();
                }
            }

            // Sử dụng chính xác PageResponse tổng của bạn
            return PageResponse.<AttachmentItemResponse>builder()
                    .items(items)
                    .nextCursor(nextCursor)
                    .build();

        } catch (Exception e) {
            log.error("Lỗi xảy ra khi truy vấn tệp tin phòng {}: {}", roomId, e.getMessage(), e);
            return PageResponse.<AttachmentItemResponse>builder().items(List.of()).nextCursor(null).build();
        }
    }



    private List<AttachmentItem> findAttachmentsByMessageId(String roomId, String messageId) {
        String partitionValue = "ROOM#" + roomId;

        QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(partitionValue)
                        .sortValue("ATTACHMENT#")
                        .build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build();

        return attachmentItemTable.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(item -> messageId.equals(item.getMessageId()))
                .collect(Collectors.toList());
    }
    private void updateAttachmentStatusByMessageId(
            String roomId,
            String messageId,
            AttachmentStatus status
    ) {
        try {
            String partitionValue = "ROOM#" + roomId;

            QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                    Key.builder()
                            .partitionValue(partitionValue)
                            .sortValue("ATTACHMENT#")
                            .build()
            );

            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .build();

            attachmentItemTable.query(request)
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .filter(item -> messageId.equals(item.getMessageId()))
                    .forEach(item -> {
                        item.setStatus(status);
                        attachmentItemTable.updateItem(item);
                    });

            log.info("Updated attachments of message {} in room {} to status {}",
                    messageId, roomId, status);

        } catch (Exception e) {
            log.error("Failed to update attachment status for message {} in room {}: {}",
                    messageId, roomId, e.getMessage(), e);
        }
    }
    @Override
    public void markAttachmentsDeletedForUserByMessageId(
            String roomId,
            String messageId,
            String userId
    ) {
        List<AttachmentItem> items = findAttachmentsByMessageId(roomId, messageId);

        for (AttachmentItem item : items) {
            List<String> deletedBy = item.getDeletedByUserIds();

            if (deletedBy == null) {
                deletedBy = new ArrayList<>();
            } else {
                deletedBy = new ArrayList<>(deletedBy);
            }

            if (!deletedBy.contains(userId)) {
                deletedBy.add(userId);
                item.setDeletedByUserIds(deletedBy);
                attachmentItemTable.updateItem(item);
            }
        }
    }

    @Override
    public void markAttachmentsRevokedByMessageId(String roomId, String messageId) {
        List<AttachmentItem> items = findAttachmentsByMessageId(roomId, messageId);

        for (AttachmentItem item : items) {
            item.setStatus(AttachmentStatus.REVOKED);
            attachmentItemTable.updateItem(item);
        }
    }
}