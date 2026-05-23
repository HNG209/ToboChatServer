package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.entities.GenericItem;
import com.teamtobo.tobochatserver.services.DataMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService {
    private final DynamoDbEnhancedClient enhancedClient;
    private final Neo4jClient neo4jClient;

    @Value("${aws.dynamodb.tableName:ToboChatTable}")
    private String tableName;

    @Override
    public void migrateFromDynamoToNeo4j() {
        log.info("Bắt đầu migration sang Neo4j");

        DynamoDbTable<GenericItem> table = enhancedClient.table(tableName,
                TableSchema.fromBean(GenericItem.class));

        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .limit(100)
                .build();

        AtomicInteger totalNodes = new AtomicInteger(0);
        AtomicInteger totalRelationships = new AtomicInteger(0);
        int pageCount = 0;

        for (Page<GenericItem> page : table.scan(scanRequest)) {
            pageCount++;
            log.info("Đang xử lý trang số: {} | Items: {}", pageCount, page.items().size());

            page.items().forEach(item -> {
                String pk = item.getPk();
                String sk = item.getSk();

                if (pk != null && pk.startsWith("USER#") && sk != null) {
                    String userId = pk.replace("USER#", "");

                    if (sk.equals("PROFILE")) {
                        syncUserNode(userId);
                        totalNodes.incrementAndGet();
                    } else if (sk.startsWith("FRIEND#")) {
                        String friendId = sk.replace("FRIEND#", "");
                        createFriendship(userId, friendId);
                        totalRelationships.incrementAndGet();
                    } else if (sk.startsWith("REQUEST#")) {
                        String targetId = sk.replace("REQUEST#", "");
                        createRequestRelationship(userId, targetId);
                        totalRelationships.incrementAndGet();
                    }
                }
            });

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                log.error("Migration thread bị gián đoạn", e);
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("Migration hoàn tất! Tổng số Node: {} | Tổng số thao tác xử lý cạnh: {}", totalNodes.get(), totalRelationships.get());
    }

    @Override
    public void clearAllNeo4jData() {
        neo4jClient.query("MATCH (n) DETACH DELETE n")
                .run();
    }

    private void syncUserNode(String userId) {
        neo4jClient.query("MERGE (u:User {id: $id})")
                .bind(userId).to("id")
                .run();
    }

    private void createFriendship(String userId1, String userId2) {
        // So sánh chuỗi để xác định node gốc luôn là node có ID nhỏ hơn lexicographically
        String fromId = userId1.compareTo(userId2) < 0 ? userId1 : userId2;
        String toId = userId1.compareTo(userId2) < 0 ? userId2 : userId1;

        String query = "MERGE (a:User {id: $fromId}) " +
                "MERGE (b:User {id: $toId}) " +
                "MERGE (a)-[:FRIEND]->(b)";

        neo4jClient.query(query)
                .bind(fromId).to("fromId")
                .bind(toId).to("toId")
                .run();
    }

    private void createRequestRelationship(String fromId, String toId) {
        String query = "MERGE (a:User {id: $fromId}) " +
                "MERGE (b:User {id: $toId}) " +
                "MERGE (a)-[:SEND_REQUEST]->(b)";

        neo4jClient.query(query)
                .bind(fromId).to("fromId")
                .bind(toId).to("toId")
                .run();
    }
}