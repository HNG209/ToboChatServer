package com.teamtobo.tobochatserver.configs;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SocketIOConfig {

    @Bean
    public SocketIOServer socketIOServer() {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();

        // Chạy trên localhost, cổng 8085
        config.setHostname("localhost");
        config.setPort(8085);

        // Cho phép mọi Frontend kết nối tới (Bỏ qua lỗi CORS)
        config.setOrigin("*");

        return new SocketIOServer(config);
    }
}