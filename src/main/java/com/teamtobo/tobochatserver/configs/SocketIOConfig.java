package com.teamtobo.tobochatserver.configs;

import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class SocketIOConfig {

    private final JwtDecoder jwtDecoder;

    @Bean
    public SocketIOServer socketIOServer() {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();

        config.setHostname("localhost");
        config.setPort(8085);
        config.setOrigin("*");

        config.setAuthorizationListener(handshakeData -> {
            String token = handshakeData.getSingleUrlParam("token");

            if (token != null && !token.isEmpty()) {
                try {
                    Jwt jwt = jwtDecoder.decode(token);
                    log.info("Xác thực thành công cho user: {}", jwt.getSubject());
                    return AuthorizationResult.SUCCESSFUL_AUTHORIZATION;

                } catch (JwtException e) {
                    log.error("Lỗi token: {}", e.getMessage());
                    return AuthorizationResult.FAILED_AUTHORIZATION;
                }
            }
            log.warn("Thiếu token");
            return AuthorizationResult.FAILED_AUTHORIZATION;
        });

        SocketIOServer server = new SocketIOServer(config);

        server.addEventListener("join_user_room", String.class, (client, userId, ackSender) -> {
            if (userId != null && !userId.trim().isEmpty()) {
                client.joinRoom(userId);
                log.info("User {} joined their personal socket room", userId);
            } else {
                log.warn("join_user_room received with empty userId");
            }
        });

        log.info("SocketIO Server started with join_user_room listener");

        return server;
    }
}