package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.PresignedUrlResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.User;
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
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
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
    private final S3Client s3Client;

    @Value("${aws.s3.bucketName}")
    private String bucketName;
    @Value("${aws.region}")
    private String region;

    @Override
    public MessageResponse getRoomMessage(String userId, String roomId, String messageId) {
        // TODO: Check xem user hiện tại (userId) có trong phòng này ko (integrity)
        Message message = messageTable.getItem(Key.builder()
                .partitionValue("ROOM#" + roomId)
                .sortValue("MSG#" + messageId)
                .build());

        if (message == null) return null;
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
            // LOGIC CŨ CHO "before", "after" HOẶC LOAD LẦN ĐẦU
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

        // 2. FILTER VÀ MAP SANG DTO
        // Lọc bỏ null và các record không phải là message
        items = items.stream()
                .filter(Objects::nonNull)
                .filter(msg -> msg.getSk() != null && msg.getSk().startsWith("MSG#"))
                .collect(Collectors.toList());

        // Thực hiện ánh xạ sang DTO duy nhất 1 lần tại đây
        List<MessageResponse> messageResponses = items.stream()
                .filter(msg -> msg.getDeletedByUserIds() == null || !msg.getDeletedByUserIds().contains(userId)) // Thêm null check cho an toàn
                .map(msg -> {
                    String messageId = msg.getSk().replace("MSG#", "");
                    boolean isSelf = userId.equals(msg.getSenderId());
                    UserResponse userResponse = userService.getUserProfile(msg.getSenderId());

                    return MessageResponse.builder()
                            .id(messageId)
                            // Tin nhắn đã thu hồi ko cần trả về content và replyTo
                            .content(msg.getMessageStatus() == MessageStatus.REVOKED ? null : msg.getContent())
                            .replyTo(msg.getReplyTo() != null && msg.getMessageStatus() != MessageStatus.REVOKED ? getRoomMessage(userId, roomId, msg.getReplyTo()) : null)
                            .createdAt(msg.getCreatedAt())
                            .isSelf(isSelf)
                            .user(userResponse)
                            .attachments(msg.getAttachments())
                            .messageStatus(msg.getMessageStatus())
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

        return new PageResponse<>(messageResponses, nextCursor, prevCursor);
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

    /**
     * Hàm hỗ trợ cắt URL để lấy S3 Key chuẩn.
     * Loại bỏ toàn bộ phần Domain và các tham số phía sau.
     */
    private String extractKeyFromUrl(String url) {
        // Tìm vị trí của temp-drafts
        int index = url.indexOf("temp-drafts");
        if (index == -1) return url;

        String key = url.substring(index);
        // Loại bỏ query string nếu có (phần sau dấu ?)
        if (key.contains("?")) {
            key = key.split("\\?")[0];
        }
        return key;
    }

    /**
     * Hàm Copy S3 có cơ chế đợi và thử lại (Retry)
     */
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
                return; // Thành công thì thoát
            } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
                attempts++;
                if (attempts >= maxRetries) throw e;

                log.warn("S3 Key chưa sẵn sàng (404), đang thử lại lần {}... Key: {}", attempts, sourceKey);
                // Đợi 500ms để S3 kịp index file vừa upload
                Thread.sleep(500);
            }
        }
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
    // nhieu tin nhan cho nhieu phong
    // transaction
    @Override
    public void forwardToMultipleRooms(String userId, String fromRoomId, List<String> toRoomIds, List<String> messageIds) {
        try {
            UserResponse userResponse = userService.getUserProfile(userId);
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
                            // TODO: giải quyết code trùng lặp
                            socketIOServer.getRoomOperations(memberId)
                                    .sendEvent("receive_message",
                                            MessageResponse.builder()
                                                    .id(msg.getSk().replace("MSG#", ""))
                                                    .user(userResponse)
                                                    .roomId(toRoomId)
                                                    .createdAt(msg.getCreatedAt())
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
