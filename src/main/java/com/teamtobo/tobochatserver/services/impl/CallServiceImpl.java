package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.services.CallService;
import io.livekit.server.RoomName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallServiceImpl implements CallService {

    @Value("${livekit.api.key}")
    private String livekitApiKey;

    @Value("${livekit.api.secret}")
    private String livekitApiSecret;

    @Override
    public String generateCallToken(String roomName, String participantName, String participantId) {
        AccessToken token = new AccessToken(livekitApiKey, livekitApiSecret);

        token.setName(participantName);
        token.setIdentity(participantId);
        token.addGrants(new RoomJoin(true), new RoomName(roomName));

        return token.toJwt();
    }
}
