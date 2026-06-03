package com.teamtobo.tobochatserver.controllers;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.teamtobo.tobochatserver.dtos.events.CallCancelledEvent;
import com.teamtobo.tobochatserver.dtos.events.CallRequestEvent;
import com.teamtobo.tobochatserver.dtos.events.WidgetMessageCreateEvent;
import com.teamtobo.tobochatserver.dtos.request.CallRequest;
import com.teamtobo.tobochatserver.dtos.response.CallResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.entities.enums.CallStatus;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.services.*;
import com.teamtobo.tobochatserver.services.handlers.ActiveRoomManager;
import com.teamtobo.tobochatserver.services.handlers.CallSessionManager;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
public class SocketIOController {
    private final ActiveRoomManager activeRoomManager;
    private final JwtDecoder jwtDecoder;
    private final UserPresenceService userPresenceService;

    public SocketIOController(SocketIOServer server,
                              CallService callService,
                              JwtDecoder jwtDecoder,
                              ActiveRoomManager activeRoomManager,
                              UserPresenceService userPresenceService) {
        this.activeRoomManager = activeRoomManager;
        this.jwtDecoder = jwtDecoder;
        this.userPresenceService = userPresenceService;
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

        // Signaling server
        server.addEventListener("request_call", CallRequest.class, (client, data, ack) -> {
            String callerId = client.get("userId");
            String roomId = data.getRoomId();
            Boolean isVideoCall = data.getIsVideoCall();

           callService.handleRequestCall(client, callerId, roomId, isVideoCall);
        });

        server.addEventListener("accept_call", CallRequest.class, (client, data, ack) -> {
            String userId = client.get("userId");
            String roomId = data.getRoomId();
            Boolean isVideoCall = data.getIsVideoCall();

            callService.handleAcceptCall(client, userId, roomId, isVideoCall, data);
        });

        server.addEventListener("join_ongoing_call", CallRequest.class, (client, data, ack) -> {
            String userId = client.get("userId");
            String roomId = data.getRoomId();
            Boolean isVideoCall = data.getIsVideoCall();

            callService.handleJoinOngoingCall(client, userId, roomId, isVideoCall);
        });

        server.addEventListener("cancel_call", CallRequest.class, (client, data, ack) -> {
            String callerId = client.get("userId");
            String roomId = data.getRoomId();

            callService.processCancelCall(callerId, roomId);
        });

        // User presence
        server.addEventListener("client_heartbeat", Object.class, (client, data, ack) -> {
            String userId = client.get("userId");
            String deviceId = client.get("deviceId");

            if (userId != null && deviceId != null) {
                userPresenceService.receiveHeartbeat(userId, deviceId);
            }
        });
    }

    private ConnectListener onConnected() {
        return client -> {
            String token = client.getHandshakeData().getSingleUrlParam("token");
            String deviceId = client.getHandshakeData().getSingleUrlParam("deviceId");

            if (token != null) {
                Jwt jwt = jwtDecoder.decode(token);
                String userId = jwt.getSubject();

                client.joinRoom(userId);
                client.set("userId", userId);
                if (deviceId != null) {
                    client.set("deviceId", deviceId);
                    userPresenceService.receiveHeartbeat(userId, deviceId);
                }

                log.info("User [{}] đã online với thiết bị [{}]", userId, deviceId);
            }
        };
    }

    private DisconnectListener onDisconnected() {
        return client -> {
            String userId = client.get("userId");
            String deviceId = client.get("deviceId");
            String socketId = client.getSessionId().toString();

            if (userId != null) {
                activeRoomManager.clearSocket(userId, socketId);
                log.info("User [{}] socket [{}] disconnected", userId, socketId);

                if (deviceId != null) {
                    // Chủ động xóa key session trên Redis ngay lập tức
                    userPresenceService.forceOffline(userId, deviceId);
                }
            }
        };
    }
}