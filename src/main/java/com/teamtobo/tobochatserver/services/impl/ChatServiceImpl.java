package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.services.ChatService;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.UserService;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
                            .messageStatus(msg.getMessageStatus())
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
    public MessageResponse getLatestMessage(String userId, String roomId) {
        try {
            String pk = "ROOM#" + roomId;

            // 1. Tạo điều kiện tìm kiếm
            Key searchKey = Key.builder()
                    .partitionValue(pk)
                    .sortValue("MSG#")
                    .build();
            QueryConditional queryConditional = QueryConditional.sortBeginsWith(searchKey);

            // 2. Lấy 1 dòng mới nhất
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .scanIndexForward(false) // Lấy từ dưới lên (Z-A)
                    .limit(1)
                    .build();

            // 3. Thực thi truy vấn
            Message latestMessage = messageTable.query(request)
                    .items() // Lấy luồng dữ liệu
                    .stream()
                    .findFirst() // Lấy phần tử đầu tiên của trang đầu tiên
                    .orElse(null);

            // 4. Nếu phòng chưa có tin nhắn nào
            if (latestMessage == null) {
                return null;
            }

            // 5. Map sang DTO
            String messageId = Helper.normalizeId(latestMessage.getSk());

            return MessageResponse.builder()
                    .id(messageId)
                    .content(latestMessage.getContent())
                    .createdAt(latestMessage.getCreatedAt() != null ? latestMessage.getCreatedAt() : messageId)
                    .isSelf(latestMessage.getSenderId().equals(userId))
                    .build();

        } catch (Exception e) {
            log.error("Lỗi khi lấy tin nhắn mới nhất phòng {}: {}", roomId, e.getMessage());
            return null;
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
                    .messageStatus(MessageStatus.NORMAL) // them trang thai
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void revokeMessage(String userId, String roomId, String messageId) {
        try {
            String pk = "ROOM#" + roomId;

            // 👇 FIX QUAN TRỌNG: thêm prefix MSG#
            String sk = "MSG#" + messageId;

            Key key = Key.builder()
                    .partitionValue(pk)
                    .sortValue(sk)
                    .build();

            // 1. Lấy message
            Message message = messageTable.getItem(r -> r.key(key));

            if (message == null) {
                throw new RuntimeException("Tin nhắn không tồn tại");
            }

            // 2. Check quyền (chỉ sender được revoke)
            if (!message.getSenderId().equals(userId)) {
                throw new RuntimeException("Không có quyền thu hồi");
            }

            // 3. Nếu đã revoke rồi thì bỏ qua
            if (message.getMessageStatus() == MessageStatus.REVOKED) {
                return;
            }

            // 4. Update trạng thái
            message.setMessageStatus(MessageStatus.REVOKED);
            //  message.setContent("Tin nhắn đã bị thu hồi");
            messageTable.updateItem(message);

            // 5. Gửi socket cho tất cả member
            List<String> memberIds = roomService.getMembersByRoomId(roomId);

            if (memberIds != null) {
                for (String memberId : memberIds) {
                    socketIOServer.getRoomOperations(memberId)
                            .sendEvent("message_revoked",
                                    Map.of(
                                            "messageId", messageId, // 👈 gửi ID gốc (không có MSG#)
                                            "roomId", roomId
                                    ));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Lỗi revoke message: {}", e.getMessage());
            throw new RuntimeException("Không thể thu hồi tin nhắn", e);
        }
    }

    // nhieu tin nhan cho nhieu phong
    // transaction
    @Override
    public void forwardToMultipleRooms(String userId, String fromRoomId, List<String> toRoomIds, List<String> messageIds) {
        try {
            // query tat ca tin nhan day du, lay content
            // moi phong tao bay nhiu tin nhan voi content
            String fromPk = "ROOM#" + fromRoomId;
            for (String toRoomId : toRoomIds) {
                String toPk = "ROOM#" + toRoomId;
                List<Message> newMessages = new ArrayList<>();
                for (String messageId : messageIds) {
                    String sk = "MSG#" + messageId;
                    //1. Lay message goc
                    Message original = messageTable.getItem(r -> r.key(
                            Key.builder()
                                    .partitionValue(fromPk)
                                    .sortValue(sk)
                                    .build()));

                    if (original == null) throw new RuntimeException("Không tìm thấy message: " + messageId);

                    // bo qua tin nhan da revoke
                    if (original.getMessageStatus() == MessageStatus.REVOKED) {
                        continue;
                    }

                    //2. Tao message moi
                    String now = Instant.now().toString();
                    String newId = UUID.randomUUID().toString();
                    Message newMsg = Message.builder().pk(toPk)
                            .sk("MSG#" + now + "#" + newId)
                            .senderId(userId)
                            .messageStatus(MessageStatus.NORMAL)
                            .content(original.getContent())
                            .createdAt(now)
                            .build();

                    newMessages.add(newMsg);
                }

                // 3. Lưu DB
                for (Message msg : newMessages) {
                    messageTable.putItem(msg);
                }

                // 4. Gửi socket
                List<String> members = roomService.getMembersByRoomId(toRoomId);

                // TODO: xử lí trong hàng đợi
                if (members != null) {
                    for (Message msg : newMessages) {
                        for (String memberId : members) {
                            if (memberId.equals(userId)) continue;
                            socketIOServer.getRoomOperations(memberId)
                                    .sendEvent("receive_message",
                                            MessageResponse.builder()
                                                    .id(msg.getSk().replace("MSG#", ""))
                                                    .roomId(toRoomId)
                                                    .content(msg.getContent())
                                                    .isSelf(false)
                                                    .build());
                        }
                    }
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Không thể forward nhiều phòng", e);
        }
    }


}
