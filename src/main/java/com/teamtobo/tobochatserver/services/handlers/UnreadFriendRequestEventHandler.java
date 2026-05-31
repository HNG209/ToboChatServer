package com.teamtobo.tobochatserver.services.handlers;

import com.teamtobo.tobochatserver.dtos.events.UnreadFriendRequestUpdateEvent;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.UserDomainService;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadFriendRequestEventHandler {
    private final UserService userService;

    @Async
    @EventListener
    public void handleUnreadFriendRequestUpdate(UnreadFriendRequestUpdateEvent event) {
        switch (event.getType()) {
            case RESET -> userService.markReadFriendRequest(event.getUserId());
            case UPDATE -> userService.increaseFriendRequestCount(event.getUserId(), event.getSenderId());
        }
    }
}
