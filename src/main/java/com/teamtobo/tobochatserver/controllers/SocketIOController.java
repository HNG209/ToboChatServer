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
            Boolean isVideoCall = data.getIsVideoCall();

            // Chặn người dùng gọi từ các thiết bị khác vào phòng nếu phòng đang có cuộc gọi
            if (callSessionManager.isCallActive(roomId)) {
                log.warn("User [{}] cố gắng bắt đầu cuộc gọi mới nhưng phòng [{}] đang có cuộc gọi", callerId, roomId);
                client.sendEvent("call_error", "Phòng này đang có cuộc gọi diễn ra.");
                return;
            }

            User caller = userService.getUserById(callerId);
            Room room = roomService.getRoomById(roomId, false);

            log.info("User [{}] đang gọi vào phòng [{}], cuộc gọi video: {}", caller.getName(), roomId, isVideoCall);

            // Tạo Token cho người gọi và trả về ngay để họ vào phòng LiveKit
            String callerToken = callService.generateCallToken(roomId, caller.getName(), callerId);
            client.sendEvent("call_joined", new CallResponse(callerToken, roomId, isVideoCall));

            // Update trạng thái cuộc gọi ngay lập tức cho các thiết bị khác của người gọi
            server.getRoomOperations(callerId).sendEvent("call_status_updated",
                    Map.of("roomId", roomId,
                            "status", CallStatus.IN_CALL));

            int totalMembers = (room != null) ? room.getMemberCount() : 2;

            // Khởi tạo trạng thái phiên gọi
            callSessionManager.initCall(roomId, callerId, totalMembers, isVideoCall);

            // Gửi sự kiện đổ chuông ở máy người khác
            eventPublisher.publishEvent(new CallRequestEvent(callerId, roomId, callerToken, isVideoCall));
        });

        server.addEventListener("accept_call", CallRequest.class, (client, data, ack) -> {
            String userId = client.get("userId");
            String roomId = data.getRoomId();

            // Kểm tra người dùng bắt máy chưa, chỉ được bắt máy trên 1 thiết bị tại 1 thời điểm
            if (callSessionManager.markAsAnswered(roomId, userId)) {
                log.info("Phòng [{}] đã có người bắt máy: {}", roomId, userId);

                // Sinh Token cho người vừa bắt máy
                User user = userService.getUserById(userId);
                String token = callService.generateCallToken(roomId, user.getName(), userId);

                // Gửi Token về cho thiết bị vừa bấm (dùng call_joined hoặc sự kiện mới)
                client.sendEvent("call_joined", new CallResponse(token, roomId, data.getIsVideoCall()));

                // Gửi lệnh tắt popup đổ chuông trên các thiết bị khác (iPad, Web...) của user này
                if (userId != null) {
                    server.getRoomOperations(userId).sendEvent("call_accepted", data);
                }
            } else {
                log.warn("User [{}] đã bắt máy phòng [{}] trước đó rồi", userId, roomId);
            }
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
                client.sendEvent("call_joined", new CallResponse(token, roomId, data.getIsVideoCall()));

                server.getRoomOperations(userId).sendEvent("call_status_updated",
                        Map.of("roomId", roomId,
                                "status", CallStatus.IN_CALL));
            } else {
                // Báo lỗi nếu họ bấm lúc cuộc gọi vừa mới tắt xong
                client.sendEvent("call_error", "Cuộc gọi này đã kết thúc.");
            }
        });

        server.addEventListener("cancel_call", CallRequest.class, (client, data, ack) -> {
            String callerId = client.get("userId");
            String roomId = data.getRoomId();
            boolean isVideoCall = callSessionManager.isVideoCall(roomId);

            // Xử lý rời cuộc gọi
            CallSessionManager.CallResult result = callSessionManager.leaveCall(roomId, callerId);

            // Tắt popup trên các thiết bị khác của user
            server.getRoomOperations(callerId).sendEvent("call_cancelled",
                    CallRequest.builder().roomId(roomId).build());

            // NẾU result == null (cuộc gọi đã bị chốt bởi người thoát trước đó)
            // HOẶC result là "ONGOING" (gọi nhóm vẫn còn người đang nói chuyện)
            if (result == null || result.getStatus().equals("ONGOING")) {
                server.getRoomOperations(callerId).sendEvent("call_status_updated",
                        Map.of("roomId", roomId,
                                "status", CallStatus.ACTIVE));
                return;
            }

            // Gửi event tắt popup cho các máy khác
            eventPublisher.publishEvent(new CallCancelledEvent(callerId, roomId));

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
                widgetMetadata.put("isVideoCall", isVideoCall ? "true" : "false");
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