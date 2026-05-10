package com.teamtobo.tobochatserver.services;

public interface CallService {
    String generateCallToken(String roomName, String participantName, String participantId);
}
