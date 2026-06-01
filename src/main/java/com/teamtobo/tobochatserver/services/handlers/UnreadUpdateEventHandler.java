package com.teamtobo.tobochatserver.services.handlers;

import com.teamtobo.tobochatserver.dtos.events.UnreadFriendRequestUpdateEvent;
import com.teamtobo.tobochatserver.dtos.events.UnreadGroupRequestUpdateEvent;
import com.teamtobo.tobochatserver.dtos.events.UnreadMessageUpdateEvent;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadUpdateEventHandler {
    private final RoomMemberService roomMemberService;
    private final UserService userService;

    @Async
    @EventListener
    public void handleUnreadMessageUpdate(UnreadMessageUpdateEvent event) {
        switch (event.getType()) {
            case RESET -> roomMemberService.markAsReadMessage(event.getUserId(), event.getRoomId());
            case UPDATE -> roomMemberService.increaseUnreadCount(event.getUserId(), event.getRoomId());
        }
    }

    @Async
    @EventListener
    public void handleUnreadGroupRequestUpdate(UnreadGroupRequestUpdateEvent event) {
        switch (event.getType()) {
            case RESET -> userService.markReadGroupRequest(event.getUserId());
            case UPDATE -> userService.increaseGroupRequestCount(event.getUserId(), event.getSenderId(), event.getRoomId());
        }
    }

    @Async
    @EventListener
    public void handleUnreadFriendRequestUpdate(UnreadFriendRequestUpdateEvent event) {
        switch (event.getType()) {
            case RESET -> userService.markReadFriendRequest(event.getUserId());
            case UPDATE -> userService.increaseFriendRequestCount(event.getUserId(), event.getSenderId());
        }
    }
}
