package com.teamtobo.tobochatserver.services.handlers;

import com.teamtobo.tobochatserver.dtos.events.MessageUpdateEvent;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.services.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageUpdateEventHandler {

    private final AttachmentService attachmentService;

    @EventListener
    public void handleMessageUpdate(MessageUpdateEvent event) {
        switch (event.getType()) {
            case REVOKED:
                attachmentService.markAttachmentsRevokedByMessageId(
                        event.getRoomId(),
                        event.getMessageId()
                );
                break;

            case DELETED_FOR_USER:
                attachmentService.markAttachmentsDeletedForUserByMessageId(
                        event.getRoomId(),
                        event.getMessageId(),
                        event.getUserId()
                );
                break;
        }
    }
}