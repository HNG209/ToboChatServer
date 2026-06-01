package com.teamtobo.tobochatserver.services.handlers;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.MemberInboxUpdateEvent;
import com.teamtobo.tobochatserver.dtos.events.UserInboxUpdateEvent;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.services.*;
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
public class InboxUpdateEventHandler {
    private final ChatService chatService;
    private final RoomService roomService;
    private final ContactService contactService;
    private final RoomMemberService roomMemberService;
    private final SocketIOServer socketIOServer;

    @Async
    @EventListener
    public void handleMemberInboxUpdate(MemberInboxUpdateEvent event) { // Update cho tất cả thành viên trong nhóm
        try {
            List<String> memberIds =
                    roomService.getMembersByRoomId(event.getRoomId());

            String now = Instant.now().toString();

            memberIds.parallelStream().forEach(memberId -> {
                try {
                    InboxStatus inboxStatus = InboxStatus.ACTIVE;

                    // Nếu cho phép bỏ qua cập nhật sender
                    if(event.isIgnoreSender() && event.getSenderId().equals(memberId)) return;

                    if (event.getRoomId().contains("_") && !memberId.equals(event.getSenderId())) {
                        FriendStatus friendStatus = contactService.getFriendStatus(event.getSenderId(), memberId);
                        inboxStatus = (friendStatus == FriendStatus.FRIEND) ? InboxStatus.ACTIVE : InboxStatus.PENDING;
                    }

                    roomMemberService.upsertMemberInbox(
                            event.getRoomId(),
                            memberId,
                            inboxStatus,
                            now,
                            event.getMessage()
                    );

                    socketIOServer.getRoomOperations(memberId)
                            .sendEvent("inbox_updated", Map.of(
                                    "message", chatService.buildLatestMessage(event.getMessage()),
                                    "inboxStatus", inboxStatus
                            ));

                } catch (Exception e) {
                    log.error("Lỗi cập nhật inbox cho member {} trong room {}: {}", memberId, event.getRoomId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Inbox update failed for room {}", event.getRoomId(), e);
        }
    }

    @Async
    @EventListener
    public void handleUserInboxUpdate(UserInboxUpdateEvent event) { // Update cho 1 người dùng cụ thể
        try {
            String now = Instant.now().toString();
            String userId = event.getUserId();
            String roomId = event.getRoomId();

            InboxStatus inboxStatus = InboxStatus.ACTIVE;

            // Tự động tính toán lại latestMessage
            MessageResponse latestMessage = chatService.getLatestMessage(userId, roomId);

            roomMemberService.upsertMemberInbox(
                    roomId,
                    userId,
                    inboxStatus,
                    now,
                    latestMessage
            );

            socketIOServer.getRoomOperations(userId)
                    .sendEvent("inbox_updated", Map.of(
                            "message", latestMessage,
                            "inboxStatus", inboxStatus
                    ));
        } catch (Exception e) {
            log.error("Inbox update failed for room {}", event.getRoomId(), e);
        }
    }
}
