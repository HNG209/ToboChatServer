package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.*;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ChatDomainServiceImpl implements ChatDomainService {
    private final DynamoDbTable<Message> messageTable;
    private final DynamoDbTable<Room> roomTable;
    private final SocketIOServer socketIOServer;
    private final RoomService roomService;
    private final UserService userService;
    private final RoomMemberService roomMemberService;

    @Override
    public void sendMessage(String senderId, String roomId, SendMessageRequest request) {
        // Tích hợp logic cho chat đơn, nhóm, người lạ
        try {
            String now = Instant.now().toString();
            String messageId = UUID.randomUUID().toString();

            String pk = "ROOM#" + roomId;
            String sk = "MSG#" + now + "#" + messageId;

            // TODO: Kiểm tra người gửi có trong phòng này ko (integrity)

            // 1. Lấy danh sách thành viên hiện có
            List<String> memberIds = roomService.getMembersByRoomId(roomId);

            // Nếu phòng chưa tồn tại
            if ((memberIds == null || memberIds.isEmpty()) && roomId.contains("_")) {
                String[] parts = roomId.split("_");
                if (parts.length == 2) {
                    memberIds = List.of(parts[0], parts[1]);
                    if(parts[0].equals(parts[1]))
                        throw new AppException(ErrorCode.ROOM_CREATE_ERROR);
                }

                // Nếu vẫn không có thành viên (có thể là mã phòng bị sai)
                if (memberIds == null || memberIds.isEmpty()) {
                    throw new AppException(ErrorCode.ROOM_NOT_FOUND);
                }

                Room roomMetadata = Room.builder()
                        .pk(pk)
                        .roomType(RoomType.DM)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                // Lưu metadata của phòng
                roomTable.putItem(roomMetadata);
            }

            // TODO: Kiểm tra người nhận có chấp nhận tin nhắn từ người lạ ko

            // 2. Lưu Message
            Message message = Message.builder()
                    .sk(sk)
                    .pk(pk)
                    .senderId(senderId)
                    .content(request.getContent())
                    .replyTo(request.getReplyTo())
                    .deletedByUserIds(new ArrayList<>())
                    // .messageType(request.getMessageType())
                    .build();

            messageTable.putItem(message);

            // 3. Upsert Inbox và Gửi Socket
            for (String memberId : memberIds) {

                InboxStatus inboxStatus = InboxStatus.ACTIVE;

                if (!memberId.equals(senderId) && roomId.contains("_")) {
                    FriendStatus friendStatus = userService.getFriendStatus(senderId, memberId);
                    // Nếu chưa là bạn, đưa vào Tin nhắn chờ (PENDING)
                    inboxStatus = (friendStatus == FriendStatus.FRIEND) ? InboxStatus.ACTIVE : InboxStatus.PENDING;
                }

                // Upsert để tạo hoặc cập nhật
                roomMemberService.upsertMemberInbox(roomId, memberId, inboxStatus, now);

                // Bỏ qua gửi socket cho chính người gửi
                if (memberId.equals(senderId)) continue;

                socketIOServer.getRoomOperations(memberId)
                        .sendEvent("receive_message",
                                MessageResponse.builder()
                                        .id(Helper.normalizeId(message.getSk()))
                                        .roomId(roomId)
                                        .content(message.getContent())
                                        .user(userService.getUserProfile(senderId))
                                        .createdAt(now)
                                        .isSelf(false)
                                        .build());
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            // Log lỗi thay vì in ra console
            System.err.println("Lỗi gửi tin nhắn phòng " + roomId + ": " + e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED);
        }
    }
}
