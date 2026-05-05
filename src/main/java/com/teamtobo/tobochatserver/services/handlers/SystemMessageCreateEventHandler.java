package com.teamtobo.tobochatserver.services.handlers;

import com.teamtobo.tobochatserver.dtos.events.SystemMessageCreateEvent;
import com.teamtobo.tobochatserver.services.ChatDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemMessageCreateEventHandler {
    private final ChatDomainService chatDomainService;

    @Async
    @EventListener
    public void handleSystemMessageCreate(SystemMessageCreateEvent event) {
        chatDomainService.sendSystemMessage(
                event.getRoomId(),
                event.getActorId(),
                event.getAction(),
                event.getMetadata());

        log.info("Đã tạo tin nhắn hệ thống");
    }
}
