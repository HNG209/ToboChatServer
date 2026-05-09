package com.teamtobo.tobochatserver.services.handlers;

import com.teamtobo.tobochatserver.dtos.events.ForwardMessageEvent;
import com.teamtobo.tobochatserver.dtos.request.SendMessageRequest;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.entities.enums.MessageType;
import com.teamtobo.tobochatserver.services.ChatDomainService;
import com.teamtobo.tobochatserver.services.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForwardMessageEventHandler {
    private final ChatService chatService;
    private final ChatDomainService chatDomainService;

    @Async
    @EventListener
    public void handleForwardMessage(ForwardMessageEvent event) {
        String userId = event.getUserId();
        String fromRoomId = event.getFromRoomId();
        List<String> toRoomIds = event.getRoomIds();
        List<String> messageIds = event.getMessageIds();

        List<Message> originalMessages = messageIds.stream()
                .map(id -> chatService.getMessageById(id, fromRoomId))
                .filter(msg -> msg.getMessageStatus() != MessageStatus.REVOKED)
                .toList();

        for (String toRoomId : toRoomIds) {
            originalMessages.forEach(message -> {
                SendMessageRequest request = SendMessageRequest.builder()
                        .messageType(MessageType.FORWARDED)
                        .content(message.getContent())
                        .attachments(message.getAttachments())
                        .build();
                chatDomainService.sendMessage(userId, toRoomId, request);
            });
        }
    }
}
