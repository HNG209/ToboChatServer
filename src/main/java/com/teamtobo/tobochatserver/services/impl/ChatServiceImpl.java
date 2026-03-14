package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.services.ChatService;
import com.teamtobo.tobochatserver.services.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final DynamoDbTable<Message> messageTable;
    private final SocketIOServer socketIOServer;
    private final RoomService roomService;
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

                    socketIOServer.getRoomOperations(memberId).sendEvent("receive_message", message);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
