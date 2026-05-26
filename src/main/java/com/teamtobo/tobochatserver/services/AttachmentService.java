package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.AttachmentItemResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.AttachmentItem;

public interface AttachmentService {
    PageResponse<AttachmentItemResponse> getRoomAttachments(String roomId, String type, int limit, String cursor);
}