package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.MemberInboxUpdateEvent;
import com.teamtobo.tobochatserver.dtos.events.UnreadMessageUpdateEvent;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.documents.Attachment;
import com.teamtobo.tobochatserver.entities.enums.*;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatDomainServiceImpl implements ChatDomainService {
    private final DynamoDbTable<Message> messageTable;
    private final SocketIOServer socketIOServer;
    private final RoomService roomService;
    private final UserService userService;
    private final RoomMemberService roomMemberService;
    private final RoomDomainService roomDomainService;
    private final ChatService chatService;
    private final S3Client s3Client;

    private final ApplicationEventPublisher eventPublisher;

    @Value("${aws.s3.bucketName}")
    private String bucketName;
    @Value("${aws.region}")
    private String region;

    @Override
    public MessageResponse sendMessage(String senderId, String roomId, SendMessageRequest request) {
        try {
            String now = Instant.now().toString();
            String messageId = UUID.randomUUID().toString();
            String pk = "ROOM#" + roomId;
            String sk = "MSG#" + now + "#" + messageId;
            String part = now + "#" + messageId;

            // đảm bảo room tồn tại
            if (roomId.contains("_")) {
                String[] parts = roomId.split("_");

                if (parts.length != 2) {
                    throw new AppException(ErrorCode.ROOM_INVALID);
                }

                String otherId;

                if (senderId.equals(parts[0])) {
                    otherId = parts[1];
                } else if (senderId.equals(parts[1])) {
                    otherId = parts[0];
                } else {
                    // sender không nằm trong room → invalid
                    throw new AppException(ErrorCode.NOT_IN_ROOM);
                }

                roomId = roomDomainService.getOrCreateDMRoom(senderId, otherId);
            }

            // Nếu là GROUP thì check có cho gửi tin nhắn không
            Room room = roomService.getRoomById(roomId, true);
            if (room != null
                    && room.getRoomType() == RoomType.GROUP
                    && !room.isAllowSendMessage()) {
                RoomMember currentMember = roomMemberService.getMemberById(senderId, roomId);
                if (currentMember.getRole() == MemberRole.MEMBER)
                    throw new AppException(ErrorCode.SEND_MESSAGE_NOT_ALLOWED);
            }

            // Xử lý attachments
            List<Attachment> finalAttachments = new ArrayList<>();

            // Nếu là tin nhắn chuyển tiếp thì không cần lưu xuống S3 nữa
            if(request.getMessageType() == MessageType.FORWARDED)
                finalAttachments = request.getAttachments();

            if (request.getMessageType() != MessageType.FORWARDED
                    && request.getAttachments() != null
                    && !request.getAttachments().isEmpty()) {
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
                    .messageType(MessageType.USER)
                    .createdAt(now)
                    .build();

            messageTable.putItem(message);

            MessageResponse messageResponse = MessageResponse.builder()
                    .id(part)
                    .tempId(request.getTempId())
                    .roomId(roomId)
                    .content(message.getContent())
                    .user(userService.getUserProfile(senderId))
                    .replyTo(chatService.getRoomMessage(senderId, roomId, request.getReplyTo()))
                    .attachments(finalAttachments) // Gửi URL sạch cho người nhận
                    .createdAt(now)
                    .build();

            // Gửi event ngay lập tức cho người dùng đang trong phòng
            socketIOServer.getRoomOperations("room:" + roomId)
                    .sendEvent("receive_message", messageResponse);

            // async upsert + socket
            eventPublisher.publishEvent(
                    new MemberInboxUpdateEvent(roomId, senderId, messageResponse, false)
            );

            eventPublisher.publishEvent(
                    new UnreadMessageUpdateEvent(senderId, roomId, UnreadUpdateType.UPDATE)
            );

            return messageResponse; // Chứa id thực tế của message đã lưu
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi gửi tin nhắn phòng {}: {}", roomId, e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED);
        }
    }

    @Override
    public void sendSystemMessage(String roomId, String actorId, SystemAction action, Map<String, String> metadata) {
        String now = Instant.now().toString();
        String messageId = UUID.randomUUID().toString();

        Message systemMsg = Message.builder()
                .pk("ROOM#" + roomId)
                .sk("MSG#" + now + "#" + messageId)
                .senderId(actorId) // Người thực hiện hành động (VD: Người đổi tên nhóm)
                .action(action)
                .messageType(MessageType.SYSTEM)
                .messageStatus(MessageStatus.NORMAL)
                .metadata(metadata) // Lưu trữ các biến số
                .createdAt(now)
                .build();

        messageTable.putItem(systemMsg);

        UserResponse actor = userService.getUserProfile(actorId);
        MessageResponse messageResponse = MessageResponse.builder()
                .id(now + "#" + messageId)
                .user(actor)
                .action(action)
                .metadata(metadata)
                .messageType(MessageType.SYSTEM)
                .roomId(roomId)
                .build();

        socketIOServer.getRoomOperations("room:" + roomId)
                .sendEvent("receive_message", messageResponse);

        eventPublisher.publishEvent(
                new MemberInboxUpdateEvent(roomId, actorId, messageResponse, false)
        );
    }

    @Override
    public MessageResponse sendWidgetMessage(String roomId, String senderId, Map<String, String> metadata) {
        String now = Instant.now().toString();
        String messageId = UUID.randomUUID().toString();
        String pk = "ROOM#" + roomId;
        String sk = "MSG#" + now + "#" + messageId;

        // Lưu Message vào DynamoDB với type là widgetType (CALL, POLL, LOCATION...)
        Message widgetMsg = Message.builder()
                .pk(pk)
                .sk(sk)
                .senderId(senderId)
                .messageType(MessageType.WIDGET)
                .messageStatus(MessageStatus.NORMAL)
                .metadata(metadata)
                .createdAt(now)
                .build();

        messageTable.putItem(widgetMsg);

        UserResponse senderProfile = userService.getUserProfile(senderId);
        MessageResponse response = MessageResponse.builder()
                .id(now + "#" + messageId)
                .roomId(roomId)
                .user(senderProfile)
                .messageType(MessageType.WIDGET)
                .metadata(metadata)
                .createdAt(now)
                .build();

        socketIOServer.getRoomOperations("room:" + roomId)
                .sendEvent("receive_message", response);

        eventPublisher.publishEvent(new MemberInboxUpdateEvent(roomId, senderId, response, false));
        eventPublisher.publishEvent(new UnreadMessageUpdateEvent(senderId, roomId, UnreadUpdateType.UPDATE));

        return response;
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