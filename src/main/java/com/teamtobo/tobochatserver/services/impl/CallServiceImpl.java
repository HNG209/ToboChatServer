package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
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
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.UserService;
import com.teamtobo.tobochatserver.services.handlers.CallSessionManager;
import com.teamtobo.tobochatserver.utils.Helper;
import io.livekit.server.RoomName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallServiceImpl implements CallService {

    @Value("${livekit.api.key}")
    private String livekitApiKey;

    @Value("${livekit.api.secret}")
    private String livekitApiSecret;

    private final RoomService roomService;
    private final UserService userService;

    private final CallSessionManager callSessionManager;
    private final ApplicationEventPublisher eventPublisher;
    private final SocketIOServer socketIOServer;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Map<String, ScheduledFuture<?>> callTimeouts = new ConcurrentHashMap<>();

    @Override
    public String generateCallToken(String roomName, String participantName, String participantId) {
        AccessToken token = new AccessToken(livekitApiKey, livekitApiSecret);

        token.setName(participantName);
        token.setIdentity(participantId);
        token.addGrants(new RoomJoin(true), new RoomName(roomName));

        return token.toJwt();
    }

    @Override
    public void processCancelCall(String callerId, String roomId) {
        ScheduledFuture<?> timeout = callTimeouts.remove(roomId);
        if (timeout != null) {
            timeout.cancel(false);
        }

        boolean isVideoCall = callSessionManager.isVideoCall(roomId);

        CallSessionManager.CallResult result = callSessionManager.leaveCall(roomId, callerId);

        socketIOServer.getRoomOperations(callerId).sendEvent("call_cancelled",
                CallRequest.builder().roomId(roomId).build());

        if (result == null || result.getStatus().equals("ONGOING")) {
            socketIOServer.getRoomOperations(callerId).sendEvent("call_status_updated",
                    Map.of("roomId", roomId,
                            "status", CallStatus.ACTIVE));
            return;
        }

        eventPublisher.publishEvent(new CallCancelledEvent(callerId, roomId));

        String originalCallerId = result.getInitiatorId();
        Room room = roomService.getRoomById(roomId, false);

        Map<String, String> widgetMetadata = new HashMap<>();
        widgetMetadata.put("widgetType", "CALL");
        widgetMetadata.put("callerId", originalCallerId);
        widgetMetadata.put("status", result.getStatus());

        if (room != null && room.getRoomType() == RoomType.GROUP) {
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
    }

    @Override
    public void handleRequestCall(SocketIOClient client, String callerId, String roomId, Boolean isVideoCall) {
        // Chặn người dùng gọi từ các thiết bị khác vào phòng nếu phòng đang có cuộc gọi
        if (callSessionManager.isCallActive(roomId)) {
            log.warn("User [{}] cố gắng bắt đầu cuộc gọi mới nhưng phòng [{}] đang có cuộc gọi", callerId, roomId);
            client.sendEvent("call_error", "Phòng này đang có cuộc gọi diễn ra.");
            return;
        }

        User caller = userService.getUserById(callerId);
        Room room = roomService.getRoomById(roomId, false);
        int totalMembers = (room != null) ? room.getMemberCount() : 2;

        if (Helper.isDMRoom(roomId)) { // Phòng cá nhân
            String otherId = Helper.getOtherId(callerId, roomId);

            // Chặn người dùng đang gọi tham gia trước nếu người kia đang trong cuộc gọi khác
            if (callSessionManager.isUserInAnyCall(otherId)) {
                socketIOServer.getRoomOperations(callerId)
                        .sendEvent("call_error", "Người dùng [" + otherId + "] hiện đang trong cuộc gọi khác");
                return;
            }
        }

        log.info("User [{}] đang gọi vào phòng [{}], cuộc gọi video: {}", caller.getName(), roomId, isVideoCall);

        // Tạo Token cho người gọi và trả về ngay để họ vào phòng LiveKit
        String callerToken = this.generateCallToken(roomId, caller.getName(), callerId);
        client.sendEvent("call_joined", new CallResponse(callerToken, roomId, isVideoCall));

        // Update trạng thái cuộc gọi ngay lập tức cho các thiết bị khác của người gọi
        socketIOServer.getRoomOperations(callerId).sendEvent("call_status_updated",
                Map.of("roomId", roomId,
                        "status", CallStatus.IN_CALL));

        // Khởi tạo trạng thái phiên gọi
        callSessionManager.initCall(roomId, callerId, totalMembers, isVideoCall);

        // Gửi sự kiện đổ chuông ở máy người khác
        eventPublisher.publishEvent(new CallRequestEvent(callerId, roomId, callerToken, isVideoCall));

        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            log.info("Cuộc gọi phòng [{}] quá 30s không ai bắt máy. Tự động tắt.", roomId);
            callTimeouts.remove(roomId); // Xóa khỏi bộ nhớ
            this.processCancelCall(callerId, roomId); // Kích hoạt tắt cuộc gọi
        }, 30, TimeUnit.SECONDS);

        // Lưu lại bộ đếm giờ theo mã phòng
        callTimeouts.put(roomId, timeoutTask);
    }

    @Override
    public void handleAcceptCall(SocketIOClient client, String userId, String roomId, Boolean isVideoCall, CallRequest data) {
        ScheduledFuture<?> timeout = callTimeouts.remove(roomId);
        if (timeout != null) {
            timeout.cancel(false);
        }

        // Kểm tra người dùng bắt máy chưa, chỉ được bắt máy trên 1 thiết bị tại 1 thời điểm
        if (callSessionManager.markAsAnswered(roomId, userId)) {
            log.info("Phòng [{}] đã có người bắt máy: {}", roomId, userId);

            // Sinh Token cho người vừa bắt máy
            User user = userService.getUserById(userId);
            String token = this.generateCallToken(roomId, user.getName(), userId);

            // Gửi Token về cho thiết bị vừa bấm (dùng call_joined hoặc sự kiện mới)
            client.sendEvent("call_joined", new CallResponse(token, roomId, isVideoCall));

            // Gửi lệnh tắt popup đổ chuông trên các thiết bị khác (iPad, Web...) của user này
            if (userId != null) {
                socketIOServer.getRoomOperations(userId).sendEvent("call_accepted", data);
            }
        } else {
            log.warn("User [{}] đã bắt máy phòng [{}] trước đó rồi", userId, roomId);
        }
    }

    @Override
    public void handleJoinOngoingCall(SocketIOClient client, String userId, String roomId, Boolean isVideoCall) {
        log.info("User [{}] đang xin tham gia trễ vào cuộc gọi phòng [{}]", userId, roomId);

        // Kiểm tra xem cuộc gọi còn diễn ra không
        if (callSessionManager.joinExistingCall(roomId, userId)) {
            User user = userService.getUserById(userId);

            // Cấp Token LiveKit mới cho người này
            String token = this.generateCallToken(roomId, user.getName(), userId);

            // Gửi Token VỀ RIÊNG MÁY CỦA NGƯỜI XIN VÀO (client.sendEvent)
            client.sendEvent("call_joined", new CallResponse(token, roomId, isVideoCall));

            socketIOServer.getRoomOperations(userId).sendEvent("call_status_updated",
                    Map.of("roomId", roomId,
                            "status", CallStatus.IN_CALL));
        } else {
            // Báo lỗi nếu họ bấm lúc cuộc gọi vừa mới tắt xong
            client.sendEvent("call_error", "Cuộc gọi này đã kết thúc.");
        }
    }

    @Override
    public CallStatus getCallStatus(String userId, String roomId) {
        if (callSessionManager.isUserInRoomCall(userId, roomId))
            return CallStatus.IN_CALL;

        if (callSessionManager.isCallActive(roomId))
            return CallStatus.ACTIVE;

        return CallStatus.INACTIVE;
    }
}
