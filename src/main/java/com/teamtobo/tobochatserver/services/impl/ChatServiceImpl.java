package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.PresignedUrlResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.entities.documents.Attachment;
import com.teamtobo.tobochatserver.services.ChatService;
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
    private final RoomService roomService;
    private final UserService userService;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${aws.s3.bucketName}")
    private String bucketName;
    private static final Region REGION = Region.AP_SOUTHEAST_2;


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
                messageResponses = messagePage.items().stream()
                        // tin nhắn đã xoá ở người dùng đó thì không trả về
                        .filter(msg -> !msg.getDeletedByUserIds().contains(userId))
                        .map(msg -> {
                    String messageId = msg.getSk().replace("MSG#", "");

                    // Kiểm tra xem tin nhắn có phải do user đang gọi API gửi không
                    boolean isSelf = userId.equals(msg.getSenderId());

                    UserResponse userResponse = userService.getUserProfile(msg.getSenderId());

                    // Build MessageResponse
                    return MessageResponse.builder()
                            .id(messageId) // Truyền ID vào đây
                            .content(msg.getContent())
                            .createdAt(msg.getCreatedAt())
                            .attachments(msg.getAttachments())
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
            log.info("Bắt đầu xử lý gửi tin nhắn. Attachments: {}",
                    (request.getAttachments() != null ? request.getAttachments().size() : 0));

            List<Attachment> processedAttachments = new ArrayList<>();

            if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
                for (Attachment attachment : request.getAttachments()) {
                    String oldUrl = attachment.getFileUrl();

                    if (oldUrl != null && oldUrl.contains("temp-drafts")) {
                        // 1. Lấy Key chuẩn (Ví dụ: temp-drafts/room1/file.png)
                        String oldKey = extractKeyFromUrl(oldUrl);
                        String newKey = oldKey.replace("temp-drafts/", "attachments/");

                        try {
                            // 2. Thực hiện Copy với cơ chế Retry
                            copyS3ObjectWithRetry(oldKey, newKey, 2); // Thử tối đa 2 lần

                            String finalUrl = String.format("https://%s.s3.%s.amazonaws.com/%s",
                                    bucketName, REGION.toString(), newKey);

                            processedAttachments.add(Attachment.builder()
                                    .fileUrl(finalUrl)
                                    .fileName(attachment.getFileName())
                                    .contentType(attachment.getContentType())
                                    .fileSize(attachment.getFileSize())
                                    .build());

                            log.info("S3 Copy Success: {} -> {}", oldKey, newKey);
                        } catch (Exception e) {
                            log.error("S3 Copy Failed sau khi retry cho key: {}. Sử dụng URL gốc.", oldKey);
                            // Fallback: Nếu copy lỗi, vẫn giữ URL cũ để không mất dữ liệu tin nhắn
                            processedAttachments.add(attachment);
                        }
                    } else {
                        processedAttachments.add(attachment);
                    }
                }
            }

            // 3. Lưu vào DynamoDB
            String now = Instant.now().toString();
            String messageId = UUID.randomUUID().toString();

            Message message = Message.builder()
                    .pk("ROOM#" + roomId)
                    .sk("MSG#" + now + "#" + messageId)
                    .senderId(senderId)
                    .content(request.getContent())
                    .messageStatus(MessageStatus.NORMAL) // them trang thai
                    .attachments(processedAttachments)
                    .createdAt(now) // Đảm bảo có timestamp
                    .deletedByUserIds(new ArrayList<>())
                    .build();

            messageTable.putItem(message);
            log.info("Lưu DynamoDB thành công. Room: {}, MessageId: {}", roomId, messageId);

            // 4. Phát Socket (Optional: Bạn tự thêm logic socket vào đây)
            // broadcastToRoom(roomId, message);

        } catch (Exception e) {
            log.error("CRITICAL ERROR SEND MESSAGE: ", e);
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
