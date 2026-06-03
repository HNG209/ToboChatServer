package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.entities.GenericItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CleanupService {
    private final DynamoDbEnhancedClient enhancedClient;

    @Value("${aws.dynamodb.tableName:ToboChatTable}")
    private String tableName;

    public void clearAll() {
        log.info("Bắt đầu dọn dẹp dữ liệu DynamoDB và reset thông số User");

        DynamoDbTable<GenericItem> table = enhancedClient.table(tableName,
                TableSchema.fromBean(GenericItem.class));

        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .limit(500)
                .build();

        List<GenericItem> itemsToDeleteBatch = new ArrayList<>();
        int totalDeleted = 0;
        int totalUpdatedUsers = 0;

        for (Page<GenericItem> page : table.scan(scanRequest)) {
            for (GenericItem item : page.items()) {
                String pk = item.getPk();
                String sk = item.getSk();

                // XỬ LÝ RIÊNG CHO USER PROFILE
                if (pk != null && pk.startsWith("USER#") && "PROFILE".equals(sk)) {
                    // Cập nhật các thông số về 0
                    item.setFriendRequestCount(0);
                    item.setGroupRequestCount(0);
                    item.setTotalUnreadContacts(0);
                    item.setTotalUnreadMessages(0);

                    // Thực hiện update ngược lại DynamoDB
                    table.updateItem(item);
                    totalUpdatedUsers++;
                    continue; // Bỏ qua không đưa vào danh sách xóa
                }

                // CÁC ITEM KHÁC: Đưa vào danh sách xóa hàng loạt (Batch)
                itemsToDeleteBatch.add(item);

                if (itemsToDeleteBatch.size() == 25) {
                    executeBatchDelete(table, itemsToDeleteBatch);
                    totalDeleted += 25;
                    itemsToDeleteBatch.clear();
                    sleepForCapacity(50);
                }
            }
        }

        // Xóa nốt các item còn sót lại
        if (!itemsToDeleteBatch.isEmpty()) {
            executeBatchDelete(table, itemsToDeleteBatch);
            totalDeleted += itemsToDeleteBatch.size();
        }

        log.info("Dọn dẹp hoàn tất. Đã xóa: {} items. Đã reset thông số cho: {} users.", totalDeleted, totalUpdatedUsers);
    }

    private void executeBatchDelete(DynamoDbTable<GenericItem> table, List<GenericItem> items) {
        WriteBatch.Builder<GenericItem> batchBuilder = WriteBatch.builder(GenericItem.class)
                .mappedTableResource(table);

        items.forEach(batchBuilder::addDeleteItem);

        BatchWriteItemEnhancedRequest batchRequest = BatchWriteItemEnhancedRequest.builder()
                .addWriteBatch(batchBuilder.build())
                .build();

        enhancedClient.batchWriteItem(batchRequest);
    }

    private void sleepForCapacity(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.warn("Thread sleep bị gián đoạn", e);
            Thread.currentThread().interrupt();
        }
    }
}