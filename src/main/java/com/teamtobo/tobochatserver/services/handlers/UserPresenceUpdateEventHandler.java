package com.teamtobo.tobochatserver.services.handlers;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.UserPresenceUpdateEvent;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomMemberResponse;
import com.teamtobo.tobochatserver.services.ContactService;
import com.teamtobo.tobochatserver.services.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPresenceUpdateEventHandler {
    private final ContactService contactService;
    private final SocketIOServer socketIOServer;

    @Async
    @EventListener
    public void handleUserPresenceUpdate(UserPresenceUpdateEvent event) {
        // Chỉ broadcast trạng thái hoạt động mới nhất cho bạn bè
        String userId = event.getUserId();
        String cursor = null;
        do {
            PageResponse<String> pageResponse = contactService.getFriendIds(userId, cursor, 20);
            List<String> friendIds = pageResponse.getItems();

            for(String friendId : friendIds) {
                socketIOServer.getRoomOperations(friendId)
                        .sendEvent("user_presence_updated", event);
            }

            cursor = pageResponse.getNextCursor();
        } while (cursor != null);
    }
}
