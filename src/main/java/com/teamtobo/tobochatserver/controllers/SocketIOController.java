package com.teamtobo.tobochatserver.controllers;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.teamtobo.tobochatserver.dtos.IcomingCallDto;
import com.teamtobo.tobochatserver.dtos.request.CallRequest;
import com.teamtobo.tobochatserver.dtos.response.CallResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomMemberResponse;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.services.CallService;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.UserService;
import com.teamtobo.tobochatserver.services.handlers.ActiveRoomManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SocketIOController {
    private final UserService userService;
    private final CallService callService;
    private final RoomMemberService roomMemberService;
    private final ActiveRoomManager activeRoomManager;
    private final JwtDecoder jwtDecoder;

    public SocketIOController(SocketIOServer server,
                              UserService userService,
                              CallService callService,
                              RoomMemberService roomMemberService,
                              JwtDecoder jwtDecoder,
                              ActiveRoomManager activeRoomManager) {
        this.userService = userService;
        this.callService = callService;
        this.roomMemberService = roomMemberService;
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

        server.addEventListener("request_call", CallRequest.class, (client, data, ack) -> {
            String callerId = client.get("userId");
            String roomId = data.getRoomId();

            User caller = userService.getUserById(callerId);

            log.info("User [{}] đang gọi vào phòng [{}]", caller.getName(), roomId);

            // Tạo Token cho người gọi và trả về ngay để họ vào phòng LiveKit
            String callerToken = callService.generateCallToken(roomId, caller.getName(), callerId);
            client.sendEvent("call_started", new CallResponse(callerToken, roomId));

            String cursor = null;
            do {
                PageResponse<RoomMemberResponse> pageResponse= roomMemberService.getRoomMembers(roomId, cursor, 50);

                for (RoomMemberResponse member : pageResponse.getItems()) {
                    String memberId = member.getId();

                    if (memberId.equals(callerId)) continue;

                    // Tạo Token LiveKit riêng cho từng người nhận
                    String receiverToken = callService.generateCallToken(roomId, member.getMember().getName(), memberId);

                    server.getRoomOperations(memberId)
                            .sendEvent("incoming_call", new IcomingCallDto(callerId, receiverToken, roomMemberService.getRoomMetadata(memberId, roomId)));

                    log.info("Đã gửi thông báo cuộc gọi đến User [{}]", memberId);
                }

                cursor = pageResponse.getNextCursor();
            } while (cursor != null);
        });

        server.addEventListener("cancel_call", CallRequest.class, (client, data, ack) -> {
            String callerId = client.get("userId");
            String roomId = data.getRoomId();

            log.info("User [{}] đã hủy cuộc gọi ở phòng [{}]", callerId, roomId);

            String cursor = null;
            do {
                PageResponse<RoomMemberResponse> pageResponse = roomMemberService.getRoomMembers(roomId, cursor, 50);

                for (RoomMemberResponse member : pageResponse.getItems()) {
                    String memberId = member.getId();

                    if (memberId.equals(callerId)) continue;

                    // Gửi tín hiệu tắt popup tới từng người
                    server.getRoomOperations(memberId)
                            .sendEvent("call_cancelled", data); // data chứa sẵn roomId
                }

                cursor = pageResponse.getNextCursor();
            } while (cursor != null);
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