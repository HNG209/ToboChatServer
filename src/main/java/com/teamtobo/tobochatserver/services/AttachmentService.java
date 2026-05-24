package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.documents.AttachmentItem;

public interface AttachmentService {
    PageResponse<AttachmentItem> getRoomAttachments(String roomId, String type, int limit, String cursor);
}