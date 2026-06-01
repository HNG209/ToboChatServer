package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.UserPresenceResponse;
import com.teamtobo.tobochatserver.entities.enums.UserPresenceStatus;

import java.util.List;
import java.util.Map;

public interface UserPresenceService {
    void receiveHeartbeat(String userId, String deviceId);
    boolean isUserOnline(String userId);
    UserPresenceResponse getUserPresenceStatus(String userId);
    Map<String, UserPresenceResponse> getUsersPresenceStatuses(List<String> userIds);
    void forceOffline(String userId, String deviceId);
}
