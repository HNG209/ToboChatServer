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
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.services.CallService;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.UserService;
import com.teamtobo.tobochatserver.services.handlers.ActiveRoomManager;
import com.teamtobo.tobochatserver.services.handlers.CallSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class SocketIOController {
    private final UserService userService;
    private final CallService callService;
    private final RoomMemberService roomMemberService;
    private final RoomService roomService;
    private final ActiveRoomManager activeRoomManager;
    private final CallSessionManager callSessionManager;
    private final JwtDecoder jwtDecoder;
    private final ApplicationEventPublisher eventPublisher;


    public SocketIOController(SocketIOServer server,
                              UserService userService,
                              CallService callService,
                              RoomMemberService roomMemberService,
                              RoomService roomService,
                              JwtDecoder jwtDecoder,
                              ActiveRoomManager activeRoomManager,
                              CallSessionManager callSessionManager,
                              ApplicationEventPublisher eventPublisher) {
        this.userService = userService;
        this.callService = callService;
        this.roomMemberService = roomMemberService;
        this.roomService = roomService;
        this.activeRoomManager = activeRoomManager;
        this.jwtDecoder = jwtDecoder;
        this.callSessionManager = callSessionManager;
        this.eventPublisher = eventPublisher;
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

            User caller = userService.getUserById(callerId);

            log.info("User [{}] đang gọi vào phòng [{}]", caller.getName(), roomId);

            // Tạo Token cho người gọi và trả về ngay để họ vào phòng LiveKit
            String callerToken = callService.generateCallToken(roomId, caller.getName(), callerId);
            client.sendEvent("call_started", new CallResponse(callerToken, roomId));

            callSessionManager.initCall(roomId, callerId);

            eventPublisher.publishEvent(new CallRequestEvent(callerId, roomId, callerToken));
        });

        server.addEventListener("accept_call", CallRequest.class, (client, data, ack) -> {
            String userId = client.get("userId");
            log.info("Phòng [{}] đã có người bắt máy", data.getRoomId());
            callSessionManager.markAsAnswered(data.getRoomId(), userId);
        });

        server.addEventListener("join_ongoing_call", CallRequest.class, (client, data, ack) -> {
            String userId = client.get("userId");
            String roomId = data.getRoomId();

            log.info("User [{}] đang xin tham gia trễ vào cuộc gọi phòng [{}]", userId, roomId);

            // Kiểm tra xem cuộc gọi còn diễn ra không
            if (callSessionManager.joinExistingCall(roomId, userId)) {
                User user = userService.getUserById(userId);

                // Cấp Token LiveKit mới cho người này
                String token = callService.generateCallToken(roomId, user.getName(), userId);

                // Gửi Token VỀ RIÊNG MÁY CỦA NGƯỜI XIN VÀO (client.sendEvent)
                client.sendEvent("call_joined", new CallResponse(token, roomId));
            } else {
                // Báo lỗi nếu họ bấm lúc cuộc gọi vừa mới tắt xong
                client.sendEvent("call_error", "Cuộc gọi này đã kết thúc.");
            }
        });

        server.addEventListener("cancel_call", CallRequest.class, (client, data, ack) -> {
            String callerId = client.get("userId");
            String roomId = data.getRoomId();

            // Xử lý rời cuộc gọi
            CallSessionManager.CallResult result = callSessionManager.leaveCall(roomId, callerId);

            // Gửi event tắt popup cho các máy khác
            eventPublisher.publishEvent(new CallCancelledEvent(callerId, roomId));

            // NẾU result == null (cuộc gọi đã bị chốt bởi người thoát trước đó)
            // HOẶC result là "ONGOING" (gọi nhóm vẫn còn người đang nói chuyện)
            if (result == null || result.getStatus().equals("ONGOING")) {
                return;
            }

            // Người chủ động bấm gọi
            String originalCallerId = result.getInitiatorId();

            Room room = roomService.getRoomById(roomId, false);

            // Cuộc gọi đã kết thúc
            Map<String, String> widgetMetadata = new HashMap<>();
            widgetMetadata.put("widgetType", "CALL");
            widgetMetadata.put("callerId", originalCallerId);
            widgetMetadata.put("status", result.getStatus());

            if (room != null && room.getRoomType() == RoomType.GROUP) {
                // Nếu là Group, đánh dấu cờ này để Frontend biết mà đưa ra GIỮA màn hình
                widgetMetadata.put("isGroupCall", "true");
            }

            if (result.getStatus().equals("ENDED")) {
                log.info("Cuộc gọi phòng [{}] kết thúc, thời lượng: {}s", roomId, result.getDuration());
                widgetMetadata.put("duration", String.valueOf(result.getDuration()));
            } else {
                log.info("Cuộc gọi phòng [{}] bị nhỡ", roomId);
            }

            eventPublisher.publishEvent(new WidgetMessageCreateEvent(roomId, originalCallerId, widgetMetadata));
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