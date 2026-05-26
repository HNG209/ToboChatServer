package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.AttachmentItemResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.AttachmentItem;
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
    public PageResponse<AttachmentItemResponse> getRoomAttachments(String roomId, String type, int limit, String cursor) {
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
                exclusiveStartKey.put("roomId", AttributeValue.builder().s(partitionValue).build());
                exclusiveStartKey.put("sortKey", AttributeValue.builder().s(cursor).build());

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
}