package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.ForwardMessageEvent;
import com.teamtobo.tobochatserver.dtos.events.UnreadMessageUpdateEvent;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.PresignedUrlResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.entities.enums.MessageType;
import com.teamtobo.tobochatserver.entities.enums.UnreadUpdateType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.entities.documents.Attachment;
import com.teamtobo.tobochatserver.services.ChatService;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.UserService;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final DynamoDbTable<Message> messageTable;
    private final SocketIOServer socketIOServer;
    private final UserService userService;
    private final RoomService roomService;
    private final S3Presigner s3Presigner;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Override
    public MessageResponse getRoomMessage(String userId, String roomId, String messageId) {
        // TODO: Check xem user hiện tại (userId) có trong phòng này ko (integrity)
        Message message = messageTable.getItem(Key.builder()
                .partitionValue("ROOM#" + roomId)
                .sortValue("MSG#" + messageId)
                .build());

        if (message == null) return null;

        boolean isRevoked = message.getMessageStatus() == MessageStatus.REVOKED;
        return MessageResponse.builder()
                .id(messageId)
                .roomId(roomId)
                .user(userService.getUserProfile(message.getSenderId()))
                .attachments(isRevoked ? null : message.getAttachments())
                .content(isRevoked ? null : message.getContent())
                .messageStatus(message.getMessageStatus())
                .createdAt(message.getCreatedAt())
                .build();
    }

    @Override
    public Message getMessageById(String messageId, String roomId) {
        Message message = messageTable.getItem(r -> r.key(
                Key.builder()
                        .partitionValue("ROOM#" + roomId)
                        .sortValue("MSG#" + messageId)
                        .build()));

        if (message == null)
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        return message;
    }

    @Override
    public MessageResponse getMessage(String messageId, String roomId) {
        Message message = getMessageById(messageId, roomId);

        return MessageResponse.builder()
                .content(message.getContent())
                .attachments(message.getAttachments())
                .createdAt(message.getCreatedAt())
                .messageStatus(message.getMessageStatus())
                .roomId(roomId)
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
        String pk = "ROOM#" + roomId;
        List<Message> items = new ArrayList<>();

        // Dùng riêng cho trường hợp "both"
        boolean hasMoreOlderBoth = false;
        boolean hasMoreNewerBoth = false;
        Map<String, AttributeValue> lastEvaluatedKeyOriginal = null;

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

            // 1.3 Gộp data
            if (afterPage != null) {
                List<Message> afterItems = new ArrayList<>(afterPage.items());
                Collections.reverse(afterItems);
                items.addAll(afterItems);
                hasMoreNewerBoth = afterPage.lastEvaluatedKey() != null && !afterPage.lastEvaluatedKey().isEmpty() && afterItems.size() == halfLimit;
            }

            if (beforePage != null) {
                List<Message> beforeItems = new ArrayList<>(beforePage.items());
                items.addAll(beforeItems);
                hasMoreOlderBoth = beforePage.lastEvaluatedKey() != null && !beforePage.lastEvaluatedKey().isEmpty() && beforeItems.size() == (halfLimit + 1);
            }

        } else {
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
                // Chỉ lấy raw items, phần map để lại phía dưới làm chung
                items = new ArrayList<>(messagePage.items());
                lastEvaluatedKeyOriginal = messagePage.lastEvaluatedKey();
            }
        }

        // Lọc bỏ null và các record không phải là message
        items = items.stream()
                .filter(Objects::nonNull)
                .filter(msg -> msg.getSk() != null && msg.getSk().startsWith("MSG#"))
                .collect(Collectors.toList());

        // DTO mapping
        List<MessageResponse> messageResponses = items.stream()
                .filter(msg -> !msg.getDeletedByUserIds().contains(userId))
                .map(msg -> {
                    String messageId = msg.getSk().replace("MSG#", "");
                    boolean isRevoked = msg.getMessageStatus() == MessageStatus.REVOKED;
                    UserResponse userResponse = userService.getUserProfile(msg.getSenderId());

                    return MessageResponse.builder()
                            .id(messageId)
                            // Tin nhắn đã thu hồi ko cần trả về content và replyTo
                            .content(isRevoked ? null : msg.getContent())
                            .replyTo(!isRevoked && msg.getReplyTo() != null ? getRoomMessage(userId, roomId, msg.getReplyTo()) : null)
                            .createdAt(msg.getCreatedAt())
                            .user(userResponse)
                            .attachments(isRevoked ? null : msg.getAttachments())
                            .messageStatus(msg.getMessageStatus())
                            // Trả về để xử lý tin nhắn hệ thống
                            .messageType(msg.getMessageType())
                            .action(msg.getAction())
                            .metadata(msg.getMetadata())
                            .build();
                }).collect(Collectors.toList());

        // 3. XỬ LÝ CURSOR
        String nextCursor = null;
        String prevCursor = null;

        if (!items.isEmpty()) {
            String first = items.get(0).getSk();
            String last = items.get(items.size() - 1).getSk();

            if ("both".equals(direction) && cursor != null && !cursor.isEmpty()) {
                prevCursor = hasMoreNewerBoth ? first : null;
                nextCursor = hasMoreOlderBoth ? last : null;
            } else {
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

                // Phát hiện hết dữ liệu
                if (lastEvaluatedKeyOriginal == null || lastEvaluatedKeyOriginal.isEmpty() || items.size() < limit) {
                    if ("before".equals(direction) || cursor == null || cursor.isEmpty()) {
                        nextCursor = null;
                    } else {
                        prevCursor = null;
                    }
                }
            }
        }

        eventPublisher.publishEvent(
                new UnreadMessageUpdateEvent(userId, roomId, UnreadUpdateType.RESET)
        );

        return new PageResponse<>(messageResponses, nextCursor, prevCursor);
    }

    @Override
    public MessageResponse getLatestMessage(String userId, String roomId) {
        try {
            String pk = "ROOM#" + roomId;

            Key searchKey = Key.builder()
                    .partitionValue(pk)
                    .sortValue("MSG#")
                    .build();

            QueryConditional queryConditional = QueryConditional.sortBeginsWith(searchKey);

            Map<String, AttributeValue> lastEvaluatedKey = null;

            do { // Loop đến tin nhắn hợp lệ nếu tin nhắn mới nhất bị xoá
                QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .scanIndexForward(false) // mới nhất -> cũ nhất
                        .limit(20); // có thể chỉnh 20–50

                if (lastEvaluatedKey != null) {
                    builder.exclusiveStartKey(lastEvaluatedKey);
                }

                Page<Message> page = messageTable.query(builder.build()).iterator().next();

                for (Message msg : page.items()) {

                    // skip nếu user đã xoá
                    if (isDeletedForUser(msg, userId)) {
                        continue;
                    }

                    // xử lý message hợp lệ đầu tiên
                    return mapToResponse(msg, userId);
                }

                lastEvaluatedKey = page.lastEvaluatedKey();

            } while (lastEvaluatedKey != null);

            // không còn message hợp lệ
            return null;

        } catch (Exception e) {
            log.error("Lỗi khi lấy tin nhắn mới nhất phòng {}: {}", roomId, e.getMessage());
            return null;
        }
    }

    private MessageResponse mapToResponse(Message message, String userId) {
        boolean isRevoked = message.getMessageStatus() == MessageStatus.REVOKED;

        String messageId = message.getSk().replaceFirst("^MSG#", "");

        StringBuilder content = new StringBuilder();

        if (message.getContent() != null && !message.getContent().isBlank()) {
            content.append(message.getContent());
        }

        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            content.append(message.getAttachments().size() > 1 ? " [attachments]" : " [attachment]");
        }

        return MessageResponse.builder()
                .id(messageId)
                .content(isRevoked ? "Tin nhắn đã được thu hồi" : content.toString())
                .messageStatus(message.getMessageStatus())
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt() : messageId)
                .build();
    }

    private boolean isDeletedForUser(Message msg, String userId) {
        return msg.getDeletedByUserIds() != null && msg.getDeletedByUserIds().contains(userId);
    }

    @Override
    public void revokeMessage(String userId, String roomId, String messageId) {
        try {
            String pk = "ROOM#" + roomId;

            // 1. Tìm tin nhắn bằng Query thay vì getItem vì chúng ta không có timestamp trong SK
            // Chúng ta query tất cả tin nhắn trong phòng và filter theo messageId
            QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                    Key.builder()
                            .partitionValue(pk)
                            .sortValue("MSG#")
                            .build()
            );

            Message message = messageTable.query(r -> r.queryConditional(queryConditional))
                    .items()
                    .stream()
                    .filter(m -> m.getSk() != null && m.getSk().endsWith(messageId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Tin nhắn không tồn tại"));

            // 2. Check quyền (chỉ sender được quyền thu hồi)
            if (!message.getSenderId().equals(userId)) {
                throw new RuntimeException("Không có quyền thu hồi");
            }

            // 3. Nếu đã thu hồi rồi thì không làm gì thêm
            if (message.getMessageStatus() == MessageStatus.REVOKED) {
                return;
            }

            // 4. Update trạng thái tin nhắn thành REVOKED
            message.setMessageStatus(MessageStatus.REVOKED);
            // Lưu ý: Không cần setContent ở Backend nếu Frontend đã xử lý hiển thị dựa trên status
            messageTable.updateItem(message);

            // 5. Gửi sự kiện Socket cho tất cả thành viên trong phòng để cập nhật UI
            List<String> memberIds = roomService.getMembersByRoomId(roomId);

            if (memberIds != null) {
                for (String memberId : memberIds) {
                    socketIOServer.getRoomOperations(memberId)
                            .sendEvent("message_revoked",
                                    Map.of(
                                            "messageId", messageId,
                                            "roomId", roomId
                                    ));
                }
            }

        } catch (RuntimeException e) {
            log.error("Lỗi nghiệp vụ revoke message: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Lỗi hệ thống khi thu hồi tin nhắn: {}", e.getMessage());
            throw new RuntimeException("Không thể thu hồi tin nhắn lúc này", e);
        }
    }

    @Override
    public void forwardMessages(String userId, String fromRoomId, List<String> messageIds, List<String> toRoomIds) {
        eventPublisher.publishEvent(
                new ForwardMessageEvent(userId, fromRoomId, toRoomIds, messageIds)
        );
    }

    @Override
    public PresignedUrlResponse generateAttachmentPresignedUrl(String fileName, String roomId, String contentType) {
        String objectKey = "temp-drafts/" + roomId + "/" + UUID.randomUUID() + "-" + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10)) // URL có hạn trong 10 phút
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        // Lấy chuỗi URL thô chứa chữ ký
        String rawUploadUrl = presignedRequest.url().toString();

        // Cắt bỏ phần chữ ký (từ dấu ? trở đi) để lấy URL thực tế
        // Lưu ý: Dùng "\\?" vì trong Java Regex, dấu ? là ký tự đặc biệt
        String cleanFileUrl = rawUploadUrl.split("\\?")[0];

        return PresignedUrlResponse.builder()
                .uploadUrl(rawUploadUrl)
                .fileUrl(cleanFileUrl)
                .build();
    }

    @Override
    public void deleteMessage(String messageId, String roomId, String userId) {
        try {
            String pk = "ROOM#" + roomId;
            String sk = "MSG#" + messageId;

            Key key = Key.builder()
                    .partitionValue(pk)
                    .sortValue(sk)
                    .build();

            // 1. Get item
            Message message = messageTable.getItem(r -> r.key(key));
            if (message == null) return;

            // 2. Lấy list hiện tại
            List<String> deletedList = message.getDeletedByUserIds();

            if (deletedList == null) {
                deletedList = new ArrayList<>();
            } else {
                deletedList = new ArrayList<>(deletedList); // clone
            }

            // 3. Add nếu chưa có
            if (!deletedList.contains(userId)) {
                deletedList.add(userId);
            } else {
                return;
            }

            // Set lại vào chính object đã get
            message.setDeletedByUserIds(deletedList);
            messageTable.updateItem(message);

            // emit socket
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
