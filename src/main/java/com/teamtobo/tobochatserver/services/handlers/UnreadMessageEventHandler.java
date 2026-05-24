package com.teamtobo.tobochatserver.services.handlers;

import com.teamtobo.tobochatserver.dtos.events.UnreadMessageUpdateEvent;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadMessageEventHandler {
    private final RoomMemberService roomMemberService;

    @Async
    @EventListener
    public void handleUnreadMessageUpdate(UnreadMessageUpdateEvent event) {
        switch (event.getType()) {
            case RESET -> roomMemberService.markAsReadMessage(event.getUserId(), event.getRoomId());
            case UPDATE -> roomMemberService.increaseUnreadCount(event.getUserId(), event.getRoomId());
        }
    }
}
