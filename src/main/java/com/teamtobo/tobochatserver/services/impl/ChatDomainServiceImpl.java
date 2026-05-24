package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.AttachmentSaveEvent;
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

            List<Attachment> finalAttachments = new ArrayList<>();
            // Lưu lại danh sách URL tạm ban đầu của Client gửi lên để truyền vào Event làm sourceKey
            List<Attachment> rawAttachments = request.getAttachments() != null ? request.getAttachments() : new ArrayList<>();
            boolean isForwarded = request.getMessageType() == MessageType.FORWARDED;

            if (isForwarded) {
                finalAttachments = rawAttachments;
            } else if (!rawAttachments.isEmpty()) {
                for (Attachment att : rawAttachments) {
                    // Tạo trước đường dẫn đích (destination) sạch sẽ dựa trên UUID
                    String fileExtension = att.getFileName().contains(".")
                            ? att.getFileName().substring(att.getFileName().lastIndexOf(".")) : "";
                    String destKey = "attachments/" + roomId + "/" + UUID.randomUUID() + fileExtension;
                    String finalUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, destKey);

                    // Khởi tạo Object Attachment chính thức mang URL mới để lưu vào Message & gửi Socket nhanh
                    finalAttachments.add(Attachment.builder()
                            .fileName(att.getFileName())
                            .contentType(att.getContentType())
                            .fileSize(att.getFileSize())
                            .fileUrl(finalUrl)
                            .build());
                }
            }

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

            if (!rawAttachments.isEmpty()) {
                eventPublisher.publishEvent(AttachmentSaveEvent.builder()
                        .roomId(roomId)
                        .messageId(part)
                        .senderId(senderId)
                        .createdAt(now)
                        .rawAttachments(rawAttachments)   // Truyền list chứa URL tạm (để bóc sourceKey)
                        .finalAttachments(finalAttachments) // Truyền list chứa URL chính thức (để bóc destKey)
                        .isForwarded(isForwarded)
                        .build());
            }

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
}