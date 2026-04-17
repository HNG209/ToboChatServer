package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.GroupAcceptRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;

public interface GroupAcceptRequestService {

    PageResponse<GroupAcceptRequestResponse> getInvites(
            String userId,
            String cursor,
            int limit
    );

    void respondInvite(
            String userId,
            String roomId,
            boolean accepted
    );
}