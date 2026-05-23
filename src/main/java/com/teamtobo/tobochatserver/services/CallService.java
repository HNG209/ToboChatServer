package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.entities.enums.CallStatus;

public interface CallService {
    String generateCallToken(String roomName, String participantName, String participantId);
    CallStatus getCallStatus(String userId, String roomId);
}
