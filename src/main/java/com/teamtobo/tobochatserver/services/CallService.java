package com.teamtobo.tobochatserver.services;

import com.corundumstudio.socketio.SocketIOClient;
import com.teamtobo.tobochatserver.dtos.request.CallRequest;
import com.teamtobo.tobochatserver.entities.enums.CallStatus;

public interface CallService {
    String generateCallToken(String roomName, String participantName, String participantId);
    CallStatus getCallStatus(String userId, String roomId);
    void processCancelCall(String callerId, String roomId);
    void handleRequestCall(SocketIOClient client, String callerId, String roomId, Boolean isVideoCall);
    void handleAcceptCall(SocketIOClient client, String userId, String roomId, Boolean isVideoCall, CallRequest data);
    void handleJoinOngoingCall(SocketIOClient client, String userId, String roomId, Boolean isVideoCall);
}
