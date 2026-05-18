package com.teamtobo.tobochatserver.services.handlers;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.IcomingCallDto;
import com.teamtobo.tobochatserver.dtos.events.CallCancelledEvent;
import com.teamtobo.tobochatserver.dtos.events.CallRequestEvent;
import com.teamtobo.tobochatserver.dtos.request.CallRequest;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomMemberResponse;
import com.teamtobo.tobochatserver.services.CallService;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallEventHandler {
    private final RoomMemberService roomMemberService;
    private final CallService callService;
    private final SocketIOServer socketIOServer;

    @Async
    @EventListener
    public void handleCallRequest(CallRequestEvent event) {
        String roomId = event.getRoomId();
        String callerId = event.getCallerId();
        String cursor = null;
        do {
            PageResponse<RoomMemberResponse> pageResponse = roomMemberService.getRoomMembers(roomId, cursor, 50);

            for (RoomMemberResponse member : pageResponse.getItems()) {
                String memberId = member.getId();

                if (memberId.equals(callerId)) continue;

                String receiverToken = callService.generateCallToken(roomId, member.getMember().getName(), memberId);
                socketIOServer.getRoomOperations(memberId)
                        .sendEvent("incoming_call",
                                new IcomingCallDto(callerId, receiverToken, roomMemberService.getRoomMetadata(memberId, roomId), event.getIsVideoCall()));

                log.info("Đã gửi thông báo cuộc gọi đến User [{}]", memberId);
            }
            cursor = pageResponse.getNextCursor();
        } while (cursor != null);
    }

    @Async
    @EventListener
    public void handleCallCancelled(CallCancelledEvent event) {
        String roomId = event.getRoomId();
        String callerId = event.getCallerId();
        String cursor = null;
        do {
            PageResponse<RoomMemberResponse> pageResponse = roomMemberService.getRoomMembers(roomId, cursor, 50);

            for (RoomMemberResponse member : pageResponse.getItems()) {
                String memberId = member.getId();

                if (memberId.equals(callerId)) continue;

                socketIOServer.getRoomOperations(memberId).sendEvent("call_cancelled",
                        CallRequest.builder().roomId(roomId).build());
            }
            cursor = pageResponse.getNextCursor();
        } while (cursor != null);
    }
}
