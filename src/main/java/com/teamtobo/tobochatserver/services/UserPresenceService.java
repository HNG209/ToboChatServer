package com.teamtobo.tobochatserver.services;

public interface UserPresenceService {
    void receiveHeartbeat(String userId, String deviceId);
    boolean isUserOnline(String userId);
    boolean forceOffline(String userId, String deviceId);
}
