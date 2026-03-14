package com.teamtobo.tobochatserver.controllers;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SocketIOController {
    private final SocketIOServer server;

    public SocketIOController(SocketIOServer server) {
        this.server = server;
        server.addConnectListener(onConnected());
        server.addDisconnectListener(onDisconnected());
    }

    private ConnectListener onConnected() {
        return client -> {
            String userId = client.getHandshakeData().getSingleUrlParam("userId");
            if (userId != null && !userId.isEmpty()) {
                client.joinRoom(userId);
                log.info("User [{}] đã online", userId);
            } else {
                client.disconnect();
            }
        };
    }

    private DisconnectListener onDisconnected() {
        return client -> {
            String userId = client.getHandshakeData().getSingleUrlParam("userId");
            if (userId != null) {
                log.info("User [{}] đã offline", userId);
            }
        };
    }
}