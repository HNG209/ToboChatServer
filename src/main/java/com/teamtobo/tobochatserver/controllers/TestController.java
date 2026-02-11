package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.entity.ChatEntity;
import com.teamtobo.tobochatserver.services.ChatService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/test")
public class TestController {

    private final ChatService chatService;

    public TestController(ChatService chatService) {
        this.chatService = chatService;
    }

    // API 1: Bơm dữ liệu mẫu (Chạy cái này trước)
    @PostMapping("/init")
    public String initData() {
        // 1. Tạo User
        chatService.save(ChatEntity.builder()
                .pk("USER#u1").sk("PROFILE")
                .entityType("USER").data("Tobo Master").build());

        chatService.save(ChatEntity.builder()
                .pk("USER#u2").sk("PROFILE")
                .entityType("USER").data("Alice Nguyen").build());

        // 2. Tạo Phòng Chat (Room metadata)
        chatService.save(ChatEntity.builder()
                .pk("ROOM#r1").sk("METADATA")
                .entityType("ROOM").data("Nhóm React Native").build());

        // 3. Tạo User trong phòng (Membership)
        chatService.save(ChatEntity.builder()
                .pk("ROOM#r1").sk("MEMBER#u1")
                .entityType("MEMBER").data("Joined").timestamp(LocalDateTime.now().toString()).build());

        // 4. Tạo Tin nhắn (Quan trọng: SK có thời gian)
        // Tin nhắn 1: Lúc 10:00
        chatService.save(ChatEntity.builder()
                .pk("ROOM#r1").sk("MSG#2026-02-09T10:00:00")
                .entityType("MESSAGE").data("Chào anh em!")
                .senderId("u1").timestamp("2026-02-09T10:00:00").build());

        // Tin nhắn 2: Lúc 10:05
        chatService.save(ChatEntity.builder()
                .pk("ROOM#r1").sk("MSG#2026-02-09T10:05:00")
                .entityType("MESSAGE").data("Hello Admin Hưng")
                .senderId("u2").timestamp("2026-02-09T10:05:00").build());

        return "Đã bơm dữ liệu thành công!";
    }

    // API 2: Lấy tin nhắn
    @GetMapping("/history/{roomId}")
    public List<ChatEntity> getHistory(@PathVariable String roomId) {
        return chatService.getRoomMessages(roomId);
    }
}
