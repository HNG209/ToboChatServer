package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.UserPresenceResponse;
import com.teamtobo.tobochatserver.entities.enums.UserPresenceStatus;

public interface UserPresenceService {
    void receiveHeartbeat(String userId, String deviceId);
    boolean isUserOnline(String userId);
    UserPresenceResponse getUserPresenceStatus(String userId);
    void forceOffline(String userId, String deviceId);
}
