package com.teamtobo.tobochatserver.services.handlers;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.InboxUpdateEvent;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InboxEventHandler {

    private final RoomService roomService;
    private final UserService userService;
    private final RoomMemberService roomMemberService;
    private final SocketIOServer socketIOServer;

    @Async
    @EventListener
    public void handleInboxUpdate(InboxUpdateEvent event) {
        try {
            List<String> memberIds =
                    roomService.getMembersByRoomId(event.getRoomId());

            String now = Instant.now().toString();

            for (String memberId : memberIds) {
                InboxStatus inboxStatus = InboxStatus.ACTIVE;

                if (event.getRoomId().contains("_") && !memberId.equals(event.getSenderId())) {
                    FriendStatus friendStatus = userService.getFriendStatus(event.getSenderId(), memberId);
                    inboxStatus = (friendStatus == FriendStatus.FRIEND) ? InboxStatus.ACTIVE : InboxStatus.PENDING;
                }

                // tạo mới hoặc cập nhật inbox cho người dùng
                roomMemberService.upsertMemberInbox(
                        event.getRoomId(),
                        memberId,
                        inboxStatus,
                        now
                );

                if (memberId.equals(event.getSenderId())) continue;

                socketIOServer.getRoomOperations(memberId)
                        .sendEvent("inbox_updated", Map.of(
                                "message", event.getMessage(),
                                "inboxStatus", inboxStatus
                        ));
            }

        } catch (Exception e) {
            log.error("Inbox update failed for room {}", event.getRoomId(), e);
        }
    }
}
