package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.entity.ChatEntity;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class ChatService {

    private final DynamoDbTable<ChatEntity> table;

    public ChatService(DynamoDbEnhancedClient enhancedClient) {
        // Liên kết code với bảng "ToboChat" trên AWS
        this.table = enhancedClient.table("ToboChatTable", TableSchema.fromBean(ChatEntity.class));
    }

    public void save(ChatEntity item) {
        table.putItem(item);
    }

    // Lấy lịch sử chat của 1 phòng
    public List<ChatEntity> getRoomMessages(String roomId) {
        // Query: PK = "ROOM#{roomId}" và SK bắt đầu bằng "MSG#"
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(k -> k.partitionValue("ROOM#" + roomId).sortValue("MSG#"));

        Iterator<ChatEntity> results = table.query(queryConditional).items().iterator();

        List<ChatEntity> messages = new ArrayList<>();
        results.forEachRemaining(messages::add);
        return messages;
    }
}