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

        // Cho phép mọi Frontend kết nối tới (Bỏ qua lỗi CORS)
        config.setOrigin("*");

        config.setAuthorizationListener(handshakeData -> {
            // Lấy token trực tiếp từ URL (ws://localhost:8085?token=eyJhbGci...)
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

        return new SocketIOServer(config);
    }
}