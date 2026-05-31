package com.teamtobo.tobochatserver.services.handlers;

import com.teamtobo.tobochatserver.dtos.events.RoomCreateEvent;
import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.services.RoomDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomCreateEventHandler {
    private final RoomDomainService roomDomainService;

    @Async
    @EventListener
    public void handleRoomCreate(RoomCreateEvent event) {
        roomDomainService.createRoom(
                event.getUserId(),
                event.getRequest(),
                event.getRoomType()
        );
    }
}
