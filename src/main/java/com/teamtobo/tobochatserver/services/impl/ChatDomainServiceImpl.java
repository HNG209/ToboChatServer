package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.documents.Attachment;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.*;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatDomainServiceImpl implements ChatDomainService {
    private final DynamoDbTable<Message> messageTable;
    private final DynamoDbTable<Room> roomTable;
    private final SocketIOServer socketIOServer;
    private final RoomService roomService;
    private final UserService userService;
    private final RoomMemberService roomMemberService;
    private final S3Client s3Client;

    @Value("${aws.s3.bucketName}")
    private String bucketName;
    @Value("${aws.region}")
    private String region;

    @Override
    public void sendMessage(String senderId, String roomId, SendMessageRequest request) {
        try {
            String now = Instant.now().toString();
            String messageId = request.getMessageId();
            String pk = "ROOM#" + roomId;
            String sk = "MSG#" + now + "#" + messageId;

            // 1. Lấy danh sách thành viên hiện có
            List<String> memberIds = roomService.getMembersByRoomId(roomId);

            // Nếu phòng chưa tồn tại (DM tự động tạo)
            if ((memberIds == null || memberIds.isEmpty()) && roomId.contains("_")) {
                String[] parts = roomId.split("_");
                if (parts.length == 2) {
                    memberIds = List.of(parts[0], parts[1]);
                    if (parts[0].equals(parts[1]))
                        throw new AppException(ErrorCode.ROOM_CREATE_ERROR);
                }

                if (memberIds == null || memberIds.isEmpty()) {
                    throw new AppException(ErrorCode.ROOM_NOT_FOUND);
                }

                Room roomMetadata = Room.builder()
                        .pk(pk)
                        .roomType(RoomType.DM)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                roomTable.putItem(roomMetadata);
            }

            // --- 2. XỬ LÝ ATTACHMENTS (PHẦN BỔ SUNG QUAN TRỌNG) ---
            List<Attachment> finalAttachments = new ArrayList<>();
            if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
                for (Attachment att : request.getAttachments()) {
                    try {
                        // Trích xuất key cũ từ URL tạm (Presigned URL client upload lên)
                        String sourceKey = extractKeyFromUrl(att.getFileUrl());

                        // Tạo key mới cho thư mục chính thức: attachments/{roomId}/{uuid}-{fileName}
                        String fileExtension = att.getFileName().contains(".")
                                ? att.getFileName().substring(att.getFileName().lastIndexOf(".")) : "";
                        String destKey = "attachments/" + roomId + "/" + UUID.randomUUID() + fileExtension;

                        // Thực hiện copy file trên S3 từ temp sang official
                        copyS3ObjectWithRetry(sourceKey, destKey, 3);

                        // Tạo URL chính thức (S3 Static URL)
                        String finalUrl = String.format("https://%s.s3.%s.amazonaws.com/%s",
                                bucketName, region, destKey);

                        finalAttachments.add(Attachment.builder()
                                .fileName(att.getFileName())
                                .contentType(att.getContentType())
                                .fileSize(att.getFileSize())
                                .fileUrl(finalUrl)
                                .build());
                    } catch (Exception e) {
                        log.error("Lỗi khi copy file đính kèm: {}", att.getFileName(), e);
                        // Có thể chọn quăng lỗi hoặc tiếp tục tùy business
                    }
                }
            }

            // 3. Lưu Message vào DB
            Message message = Message.builder()
                    .sk(sk)
                    .pk(pk)
                    .senderId(senderId)
                    .content(request.getContent())
                    .replyTo(request.getReplyTo())
                    .deletedByUserIds(new ArrayList<>())
                    .attachments(finalAttachments) // Sử dụng list đã qua xử lý S3
                    .messageStatus(MessageStatus.NORMAL)
                    .createdAt(now)
                    .build();

            messageTable.putItem(message);

            // 4. Upsert Inbox và Gửi Socket cho các member
            for (String memberId : memberIds) {
                InboxStatus inboxStatus = InboxStatus.ACTIVE;

                if (!memberId.equals(senderId) && roomId.contains("_")) {
                    FriendStatus friendStatus = userService.getFriendStatus(senderId, memberId);
                    inboxStatus = (friendStatus == FriendStatus.FRIEND) ? InboxStatus.ACTIVE : InboxStatus.PENDING;
                }

                roomMemberService.upsertMemberInbox(roomId, memberId, inboxStatus, now);

                if (memberId.equals(senderId)) continue;

                socketIOServer.getRoomOperations(memberId)
                        .sendEvent("receive_message",
                                MessageResponse.builder()
                                        .id(Helper.normalizeId(message.getSk()))
                                        .roomId(roomId)
                                        .content(message.getContent())
                                        .user(userService.getUserProfile(senderId))
                                        .attachments(finalAttachments) // Gửi URL sạch cho người nhận
                                        .createdAt(now)
                                        .isSelf(false)
                                        .build());
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi gửi tin nhắn phòng {}: {}", roomId, e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED);
        }
    }

    /**
     * Hàm hỗ trợ cắt URL để lấy S3 Key chuẩn.
     * Loại bỏ toàn bộ phần Domain và các tham số phía sau.
     */
    private String extractKeyFromUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            // Giải mã URL (biến %20 thành khoảng trắng, v.v.)
            String decodedUrl = java.net.URLDecoder.decode(url, "UTF-8");

            int index = decodedUrl.indexOf("temp-drafts");
            if (index == -1) return decodedUrl;

            String key = decodedUrl.substring(index);
            // Loại bỏ phần query string phía sau dấu ? nếu có
            if (key.contains("?")) {
                key = key.split("\\?")[0];
            }
            return key;
        } catch (Exception e) {
            log.error("Lỗi trích xuất S3 Key từ URL: {}", url);
            return url;
        }
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
}