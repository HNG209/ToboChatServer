package com.teamtobo.tobochatserver.services.handlers;

import com.teamtobo.tobochatserver.dtos.events.UnreadGroupRequestUpdateEvent;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadGroupRequestEventHandler {
    private final UserService userService;

    @Async
    @EventListener
    public void handleUnreadGroupRequestUpdate(UnreadGroupRequestUpdateEvent event) {
        switch (event.getType()) {
            case RESET -> userService.markReadGroupRequest(event.getUserId());
            case UPDATE -> userService.increaseGroupRequestCount(event.getUserId());
        }
    }
}
