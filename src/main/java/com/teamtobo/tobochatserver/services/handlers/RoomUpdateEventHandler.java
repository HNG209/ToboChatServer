package com.teamtobo.tobochatserver.services.handlers;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.RoomUpdateEvent;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomMemberResponse;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomUpdateEventHandler {
    private final SocketIOServer socketIOServer;
    private final RoomMemberService roomMemberService;

    @Async
    @EventListener
    public void handleRoomUpdate(RoomUpdateEvent event) {
        // Gửi sự kiện cho các member để cập nhật lại phòng trong inbox
        String cursor = null;
        do {
            PageResponse<RoomMemberResponse> pageResponse = roomMemberService.getRoomMembers(event.getRoomId(), cursor, 10);
            List<RoomMemberResponse> members = pageResponse.getItems();

            for(RoomMemberResponse member: members) {
                socketIOServer.getRoomOperations(member.getId())
                        .sendEvent("room_updated", event);
            }

            cursor = pageResponse.getNextCursor();
        } while (cursor != null);
    }
}
