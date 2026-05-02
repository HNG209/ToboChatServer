package com.teamtobo.tobochatserver.controllers;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.teamtobo.tobochatserver.services.handlers.ActiveRoomManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SocketIOController {
    private final ActiveRoomManager activeRoomManager;
    private final JwtDecoder jwtDecoder;

    public SocketIOController(SocketIOServer server, JwtDecoder jwtDecoder, ActiveRoomManager activeRoomManager) {
        this.activeRoomManager = activeRoomManager;
        this.jwtDecoder = jwtDecoder;
        server.addConnectListener(onConnected());
        server.addDisconnectListener(onDisconnected());

        server.addEventListener("join_room", String.class, (client, roomId, ack) -> {
            String userId = client.get("userId");
            String socketId = client.getSessionId().toString();

            activeRoomManager.join(userId, socketId, roomId);

            client.joinRoom("room:" + roomId);
        });

        server.addEventListener("leave_room", String.class, (client, roomId, ack) -> {
            String userId = client.get("userId");
            String socketId = client.getSessionId().toString();

            activeRoomManager.leave(userId, socketId, roomId);

            client.leaveRoom("room:" + roomId);
        });
    }

    private ConnectListener onConnected() {
        return client -> {
            String token = client.getHandshakeData().getSingleUrlParam("token");
            if (token != null) {
                Jwt jwt = jwtDecoder.decode(token);
                String userId = jwt.getSubject();

                client.joinRoom(userId);
                client.set("userId", userId);

                log.info("User [{}] đã online", userId);
            }
        };
    }

    private DisconnectListener onDisconnected() {
        return client -> {

            String userId = client.get("userId");
            String socketId = client.getSessionId().toString();

            if (userId != null) {

                activeRoomManager.clearSocket(userId, socketId);

                log.info("User [{}] socket [{}] disconnected", userId, socketId);
            }
        };
    }
}