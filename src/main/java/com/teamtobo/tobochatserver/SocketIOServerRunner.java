package com.teamtobo.tobochatserver;

import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocketIOServerRunner implements CommandLineRunner {

    private final SocketIOServer server;

    @Override
    public void run(String... args) {
        server.start();
        log.info("Socket.IO server running on port 8085");
    }

    // Tự động tắt server socket khi tắt ứng dụng Spring Boot
    @PreDestroy
    public void stopSocketServer() {
        server.stop();
        log.info("Socket.IO server stopped");
    }
}