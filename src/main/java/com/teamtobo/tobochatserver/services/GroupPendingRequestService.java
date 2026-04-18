package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.GroupPendingRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;

public interface GroupPendingRequestService {
    PageResponse<GroupPendingRequestResponse> getPending(String roomId, String userId, int limit);
}
