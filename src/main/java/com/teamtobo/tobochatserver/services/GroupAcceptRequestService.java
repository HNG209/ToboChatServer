package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.GroupAcceptRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;

public interface GroupAcceptRequestService {
    PageResponse<GroupAcceptRequestResponse> getInvites(
            String userId,
            String cursor,
            int limit
    );
    RoomResponse respondInvite(
            String userId,
            String roomId,
            boolean accepted
    );
}