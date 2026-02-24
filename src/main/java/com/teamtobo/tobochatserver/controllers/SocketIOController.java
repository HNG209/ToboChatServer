package com.teamtobo.tobochatserver.controllers;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SocketIOController {

    private final SocketIOServer server;

    // Bản đồ lưu trữ người dùng đang online: Key = userId, Value = SessionId (của Socket.IO)
    // Dùng ConcurrentHashMap để tránh lỗi khi có nhiều luồng cùng truy cập
    private final Map<String, UUID> connectedUsers = new ConcurrentHashMap<>();

    public SocketIOController(SocketIOServer server) {
        this.server = server;

        server.addConnectListener(onConnected());
        server.addDisconnectListener(onDisconnected());

        // Sự kiện gửi tin nhắn riêng (Chat 1-1)
        server.addEventListener("private_message", ChatMessage.class, onPrivateMessageReceived());
    }

    private ConnectListener onConnected() {
        return client -> {
            // Lấy userId từ URL do FE gửi lên (Ví dụ: ws://localhost:8085?userId=u1)
            String userId = client.getHandshakeData().getSingleUrlParam("userId");

            if (userId != null) {
                // Lưu vào "Bản đồ"
                connectedUsers.put(userId, client.getSessionId());
                log.info("🟢 User [{}] đã online với Session: {}", userId, client.getSessionId());
            } else {
                log.warn("⚠️ Client kết nối nhưng không cung cấp userId. Đang ngắt kết nối...");
                client.disconnect();
            }
        };
    }

    private DisconnectListener onDisconnected() {
        return client -> {
            String userId = client.getHandshakeData().getSingleUrlParam("userId");
            if (userId != null) {
                // Xóa khỏi "Bản đồ" khi tắt app/rớt mạng
                connectedUsers.remove(userId);
                log.info("🔴 User [{}] đã offline", userId);
            }
        };
    }

    private DataListener<ChatMessage> onPrivateMessageReceived() {
        return (client, data, ackSender) -> {
            // Người gửi chính là người đang giữ cái socket này
            String senderId = client.getHandshakeData().getSingleUrlParam("userId");
            String receiverId = data.getReceiverId();

            log.info("📩 [{}] nhắn cho [{}]: {}", senderId, receiverId, data.getContent());

            // 1. Tìm trong map xem người nhận có đang online không?
            UUID receiverSessionId = connectedUsers.get(receiverId);

            if (receiverSessionId != null) {
                // 2. Nếu online -> Bắn thẳng sự kiện "receive_message" vào mặt người đó
                data.setSenderId(senderId); // Bổ sung ID người gửi để FE biết ai nhắn
                server.getClient(receiverSessionId).sendEvent("receive_message", data);
            } else {
                // 3. Nếu offline -> (Tương lai: Gọi Firebase Push Notification hoặc kệ nó, FE sẽ lấy từ DB sau)
                log.info("💤 User [{}] đang offline, không thể gửi real-time", receiverId);
            }
        };
    }
}

// Cập nhật lại DTO
@Data
class ChatMessage {
    private String senderId;   // Người gửi (Backend tự điền)
    private String receiverId; // Gửi cho ai? (Frontend gửi lên)
    private String content;    // Nội dung
}