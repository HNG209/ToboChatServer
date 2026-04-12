package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
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
    public MessageResponse getRoomMessage(String userId, String roomId, String messageId) {
        Message message = messageTable.getItem(Key.builder()
                .partitionValue("ROOM#" + roomId)
                .sortValue("MSG#" + messageId)
                .build());

        if(message == null) return null;
        return MessageResponse.builder()
                .id(messageId)
                .roomId(roomId)
                .user(userService.getUserProfile(message.getSenderId()))
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    @Override
    public PageResponse<MessageResponse> getMessages(
            String userId,
            String roomId,
            String cursor,
            int limit,
            String direction // "before" | "after" | "both"
    ) {
        try {
            String pk = "ROOM#" + roomId;
            List<Message> items = new ArrayList<>();

            // Biến phân trang dùng riêng cho trường hợp "both"
            boolean hasMoreOlderBoth = false;
            boolean hasMoreNewerBoth = false;
            Map<String, AttributeValue> lastEvaluatedKeyOriginal = null;

            // 1. FETCH DATA TỪ DYNAMODB
            if ("both".equals(direction) && cursor != null && !cursor.isEmpty()) {
                Key key = Key.builder().partitionValue(pk).sortValue(cursor).build();
                int halfLimit = limit / 2;

                // 1.1 Fetch AFTER (Tin mới hơn, tiến về tương lai)
                QueryEnhancedRequest afterReq = QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.sortGreaterThan(key))
                        .scanIndexForward(true)
                        .limit(halfLimit)
                        .build();
                Page<Message> afterPage = messageTable.query(afterReq).stream().findFirst().orElse(null);

                // 1.2 Fetch BEFORE (Tin cũ hơn + lấy chính cursor hiện tại)
                QueryEnhancedRequest beforeReq = QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.sortLessThanOrEqualTo(key))
                        .scanIndexForward(false)
                        .limit(halfLimit + 1)
                        .build();
                Page<Message> beforePage = messageTable.query(beforeReq).stream().findFirst().orElse(null);

                // 1.3 Gộp data (Đảm bảo list trả về luôn từ Mới nhất -> Cũ nhất để đồng nhất với logic gốc)
                if (afterPage != null) {
                    List<Message> afterItems = new ArrayList<>(afterPage.items());
                    Collections.reverse(afterItems); // ScanForward=true trả ra Cũ -> Mới, đảo lại thành Mới -> Cũ
                    items.addAll(afterItems);

                    Map<String, AttributeValue> lastKey = afterPage.lastEvaluatedKey();
                    hasMoreNewerBoth = lastKey != null && !lastKey.isEmpty() && afterItems.size() == halfLimit;
                }

                if (beforePage != null) {
                    List<Message> beforeItems = new ArrayList<>(beforePage.items()); // Đã là Mới -> Cũ sẵn
                    items.addAll(beforeItems);

                    Map<String, AttributeValue> lastKey = beforePage.lastEvaluatedKey();
                    hasMoreOlderBoth = lastKey != null && !lastKey.isEmpty() && beforeItems.size() == (halfLimit + 1);
                }

            } else {
                // LOGIC CŨ CHO "before", "after" HOẶC LOAD LẦN ĐẦU (Không thay đổi)
                QueryConditional queryConditional;
                if (cursor != null && !cursor.isEmpty()) {
                    Key key = Key.builder().partitionValue(pk).sortValue(cursor).build();
                    if ("before".equals(direction)) {
                        queryConditional = QueryConditional.sortLessThan(key);
                    } else {
                        queryConditional = QueryConditional.sortGreaterThan(key);
                    }
                } else {
                    queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(pk).build());
                }

                boolean scanForward = !"before".equals(direction);
                QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .scanIndexForward(scanForward)
                        .limit(limit)
                        .build();

                Page<Message> messagePage = messageTable.query(request).stream().findFirst().orElse(null);

                if (messagePage != null) {
                    items = new ArrayList<>(messagePage.items());
                    lastEvaluatedKeyOriginal = messagePage.lastEvaluatedKey();
                }
            }

            // 2. FILTER VÀ MAP SANG DTO
            // Filter đúng prefix MSG#
            items = items.stream()
                    .filter(Objects::nonNull)
                    .filter(msg -> msg.getSk() != null && msg.getSk().startsWith("MSG#"))
                    .collect(Collectors.toList());

            // Map sang DTO
            List<MessageResponse> messageResponses = items.stream()
                    .filter(msg -> !msg.getDeletedByUserIds().contains(userId))
                    .map(msg -> {
                        String messageId = msg.getSk().replace("MSG#", "");
                        boolean isSelf = userId.equals(msg.getSenderId());
                        UserResponse userResponse = userService.getUserProfile(msg.getSenderId());

                        return MessageResponse.builder()
                                .id(messageId)
                                .content(msg.getContent())
                                .replyTo(getRoomMessage(userId, roomId, msg.getReplyTo()))
                                .createdAt(msg.getCreatedAt())
                                .isSelf(isSelf)
                                .user(userResponse)
                                .build();
                    }).collect(Collectors.toList());

            // 3. XỬ LÝ CURSOR
            String nextCursor = null;
            String prevCursor = null;

            if (!items.isEmpty()) {
                String first = items.get(0).getSk();
                String last = items.get(items.size() - 1).getSk();

                if ("both".equals(direction) && cursor != null && !cursor.isEmpty()) {
                    // Cấp cursor dựa vào check hasMore của "both"
                    prevCursor = hasMoreNewerBoth ? first : null; // load mới hơn
                    nextCursor = hasMoreOlderBoth ? last : null;  // load cũ hơn
                } else {
                    // Logic cursor nguyên bản
                    if (cursor == null || cursor.isEmpty()) {
                        nextCursor = last;
                    } else if ("before".equals(direction)) {
                        prevCursor = first;
                        nextCursor = last;
                    } else {
                        prevCursor = last;
                        nextCursor = first;
                        Collections.reverse(messageResponses);
                    }

                    // Detect hết data nguyên bản
                    if (lastEvaluatedKeyOriginal == null || lastEvaluatedKeyOriginal.isEmpty() || items.size() < limit) {
                        if ("before".equals(direction)) {
                            nextCursor = null;
                        } else {
                            prevCursor = null;
                        }
                    }
                }
            }

            return new PageResponse<>(messageResponses, nextCursor, prevCursor);

        } catch (Exception e) {
            log.error("Lỗi khi lấy danh sách tin nhắn phòng {}, cursor {}: {}", roomId, cursor, e.getMessage());
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
                    .replyTo(request.getReplyTo())
                    .deletedByUserIds(new ArrayList<>())
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
    @Override
    public void deleteMessage(String messageId, String roomId, String userId) {
        try {
            String cleanRoomId = roomId.contains("_")
                    ? roomId.substring(0, roomId.indexOf("_"))
                    : roomId;

            String pk = "ROOM#" + cleanRoomId + "_" + userId;

            QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                    Key.builder()
                            .partitionValue(pk)
                            .sortValue("MSG#")
                            .build()
            );

            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .scanIndexForward(false)
                    .limit(30)
                    .build();

            List<Message> messages = messageTable.query(request)
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .toList();


            Message foundMessage = null;
            for (Message msg : messages) {
                String sk = msg.getSk();
                if (sk != null && sk.contains(messageId)) {
                    foundMessage = msg;
                    break;
                }
            }

            if (foundMessage == null)
                return;

            // Soft delete
            List<String> deletedList = new ArrayList<>(
                    foundMessage.getDeletedByUserIds() != null
                            ? foundMessage.getDeletedByUserIds()
                            : new ArrayList<>()
            );

            if (!deletedList.contains(userId)) {
                deletedList.add(userId);
            }

            Message updatedMessage = Message.builder()
                    .pk(foundMessage.getPk())
                    .sk(foundMessage.getSk())
                    .content(foundMessage.getContent())
                    .senderId(foundMessage.getSenderId())
                    .messageType(foundMessage.getMessageType())
                    .createdAt(foundMessage.getCreatedAt())
                    .deletedByUserIds(deletedList)
                    .build();

            messageTable.updateItem(updatedMessage);

            // Gửi sự kiện cho chính mình (xoá ngay lập tức nếu đang đăng nhập trên thiết bị khác)
            socketIOServer.getRoomOperations(userId)
                    .sendEvent("delete_message", MessageResponse.builder()
                            .id(messageId)
                            .roomId(roomId)
                            .build());

        } catch (Exception e) {
            throw new RuntimeException("Không thể xoá tin nhắn", e);
        }
    }
}
