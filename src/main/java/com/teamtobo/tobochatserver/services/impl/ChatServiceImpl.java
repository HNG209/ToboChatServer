package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.services.ChatService;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.UserService;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final DynamoDbTable<Message> messageTable;
    private final SocketIOServer socketIOServer;
    private final RoomService roomService;
    private final UserService userService;

    @Override
    public PageResponse<MessageResponse> getMessages(String userId, String roomId, String cursor, int limit) {
        try {
            String pk = "ROOM#" + roomId;

            // 1. Tạo điều kiện truy vấn: Lấy tất cả item có SK bắt đầu bằng "MSG#"
            Key searchKey = Key.builder()
                    .partitionValue(pk)
                    .sortValue("MSG#")
                    .build();
            QueryConditional queryConditional = QueryConditional.sortBeginsWith(searchKey);

            // 2. Thiết lập Pagination (Cursor)
            Map<String, AttributeValue> startKey = null;
            if (cursor != null && !cursor.isEmpty()) {
                // Reconstruct lại LastEvaluatedKey từ cursor (ví dụ: "MSG#2026-02-11T12:00:00")
                startKey = new HashMap<>();
                startKey.put("pk", AttributeValue.builder().s(pk).build());
                startKey.put("sk", AttributeValue.builder().s(cursor).build());
            }

            // 3. Cấu hình request truy vấn DynamoDB
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .scanIndexForward(false) // Lấy tin nhắn mới nhất trước (Z-A)
                    .limit(limit)
                    .exclusiveStartKey(startKey)
                    .build();

            // 4. Thực thi truy vấn lấy 1 trang dữ liệu
            Page<Message> messagePage = messageTable.query(request).stream().findFirst().orElse(null);

            List<MessageResponse> messageResponses = new ArrayList<>();
            String nextCursor = null;

            if (messagePage != null) {
                // 5. Map dữ liệu từ Entity sang DTO
                messageResponses = messagePage.items().stream().map(msg -> {
                    // Lấy ID tin nhắn:
                    // Nếu Entity Message của bạn có trường ID riêng (như messageId), hãy dùng msg.getMessageId()
                    // Nếu không, ta có thể cắt bỏ chữ "MSG#" để lấy đoạn thời gian làm ID tạm thời cho Frontend render
                    String messageId = msg.getSk().replace("MSG#", "");

                    // Kiểm tra xem tin nhắn có phải do user đang gọi API gửi không
                    boolean isSelf = userId.equals(msg.getSenderId());

                    // TODO: Tối ưu truy vấn
                    UserResponse userResponse = userService.getUserProfile(msg.getSenderId());

                    // Build MessageResponse
                    return MessageResponse.builder()
                            .id(messageId) // Truyền ID vào đây
                            .content(msg.getContent())
                            .createdAt(msg.getCreatedAt())
                            .isSelf(isSelf)
                            .user(userResponse)
                            // .messageType(msg.getMessageType()) // Bật lên nếu Entity có trường này
                            .build();
                }).collect(Collectors.toList());

                // 6. Lấy cursor cho lần gọi "Load More" tiếp theo
                Map<String, AttributeValue> lastEvaluatedKey = messagePage.lastEvaluatedKey();
                if (lastEvaluatedKey != null && lastEvaluatedKey.containsKey("sk")) {
                    // Trả về nguyên vẹn chuỗi SK (ví dụ: "MSG#2026-02-11T11:50:00") cho Frontend cất giữ
                    nextCursor = lastEvaluatedKey.get("sk").s();
                }
            }

            // 7. Trả về Response bọc danh sách và con trỏ
            return new PageResponse<>(messageResponses, nextCursor);

        } catch (Exception e) {
            log.error("Lỗi khi lấy danh sách tin nhắn phòng {}: {}", roomId, e.getMessage());
            throw new RuntimeException("Không thể tải tin nhắn lúc này", e);
        }
    }

    @Override
    public void sendMessage(String senderId, String roomId, SendMessageRequest request) {
        try {
            String now = Instant.now().toString();
            String messageId = UUID.randomUUID().toString();

            // PK SK
            String pk = "ROOM#" + roomId;
            String sk = "MSG#" + now + "#" + messageId;

            Message message = Message.builder()
                    .sk(sk)
                    .pk(pk)
                    .senderId(senderId)
                    .content(request.getContent())
                    .build();

            // 1. Lưu message
            messageTable.putItem(message);

            List<String> memberIds = roomService.getMembersByRoomId(roomId);

            // 2. Gửi qua socket.io cho từng người trong phòng/nhóm
            if (memberIds != null) {
                for (String memberId : memberIds) {
                    // Bỏ qua người gửi
                    if (memberId.equals(senderId)) continue;
                    socketIOServer.getRoomOperations(memberId)
                            .sendEvent("receive_message",
                                    MessageResponse.builder()
                                            .id(Helper.normalizeId(message.getSk()))
                                            .roomId(roomId)
                                            .content(message.getContent())
                                            .user(userService.getUserProfile(senderId))
                                            .messageType(message.getMessageType())
                                            .createdAt(message.getCreatedAt())
                                            .isSelf(false)
                                            .build());
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
