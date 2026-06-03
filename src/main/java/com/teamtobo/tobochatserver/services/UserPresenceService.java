package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.UserPresenceResponse;

import java.util.List;
import java.util.Map;

public interface UserPresenceService {
    void receiveHeartbeat(String userId, String deviceId);
    boolean isUserOnline(String userId);
    UserPresenceResponse getUserPresenceStatus(String userId, String otherId);
    Map<String, UserPresenceResponse> getUsersPresenceStatuses(String userId, List<String> otherIds);
    void forceOffline(String userId, String deviceId);
}
